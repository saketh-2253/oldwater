package com.modernice.integration.connector.hubspot;

import com.modernice.integration.connector.hubspot.dto.HubSpotCompanyRequest;
import com.modernice.integration.connector.hubspot.dto.HubSpotContactRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HubSpot CRM v3 client. Active for every profile except "local", where
 * {@link MockHubSpotClient} simulates the same contract in memory.
 */
@Component
@Profile("!local")
public class RealHubSpotClient implements HubSpotClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RealHubSpotClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final String CONTACTS = "/crm/v3/objects/contacts";
    private static final String COMPANIES = "/crm/v3/objects/companies";

    private final WebClient webClient;
    private final HubSpotProperties properties;

    public RealHubSpotClient(WebClient.Builder webClientBuilder, HubSpotProperties properties) {
        if (!StringUtils.hasText(properties.getAccessToken())) {
            throw new IllegalStateException(
                    "integration.hubspot.access-token (HUBSPOT_ACCESS_TOKEN) must be set when the local profile is not active");
        }
        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Mono<Map<String, Object>> getContact(String hubSpotContactId) {
        return getObject(CONTACTS, hubSpotContactId);
    }

    @Override
    public Mono<String> createContact(HubSpotContactRequest request) {
        return createObject(CONTACTS, request.properties());
    }

    @Override
    public Mono<Void> updateContact(String hubSpotContactId, HubSpotContactRequest request) {
        return updateObject(CONTACTS, hubSpotContactId, request.properties());
    }

    // HubSpot assigns ids itself, so a missing contact is created rather than created under the given id.
    @Override
    public Mono<Void> upsertContact(String hubSpotContactId, HubSpotContactRequest request) {
        return updateObject(CONTACTS, hubSpotContactId, request.properties())
                .onErrorResume(NotFound.class, ignored -> {
                    LOGGER.info("HubSpot contact {} not found; creating a new contact", hubSpotContactId);
                    return createObject(CONTACTS, request.properties()).then();
                });
    }

    @Override
    public Mono<Map<String, Object>> getCompany(String hubSpotCompanyId) {
        return getObject(COMPANIES, hubSpotCompanyId);
    }

    @Override
    public Mono<String> createCompany(HubSpotCompanyRequest request) {
        return createObject(COMPANIES, request.properties());
    }

    @Override
    public Mono<Void> updateCompany(String hubSpotCompanyId, HubSpotCompanyRequest request) {
        return updateObject(COMPANIES, hubSpotCompanyId, request.properties());
    }

    // GET /crm/v3/objects/{type}/{id}; an empty Mono means the record does not exist.
    private Mono<Map<String, Object>> getObject(String path, String id) {
        return webClient.get()
                .uri(path + "/{id}", id)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(this::extractProperties)
                .onErrorResume(WebClientResponseException.NotFound.class, ignored -> Mono.empty())
                .retryWhen(retrySpec());
    }

    // POST /crm/v3/objects/{type}
    private Mono<String> createObject(String path, Map<String, Object> properties) {
        return webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("properties", toHubSpotValues(properties)))
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(body -> String.valueOf(body.get("id")))
                .doOnNext(id -> LOGGER.info("HubSpot created {} {}", path, id))
                .retryWhen(retrySpec());
    }

    // PATCH /crm/v3/objects/{type}/{id}
    private Mono<Void> updateObject(String path, String id, Map<String, Object> properties) {
        return webClient.patch()
                .uri(path + "/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("properties", toHubSpotValues(properties)))
                .retrieve()
                .toBodilessEntity()
                .doOnNext(ignored -> LOGGER.info("HubSpot updated {} {}", path, id))
                .then()
                .onErrorMap(WebClientResponseException.NotFound.class, e -> new NotFound(e))
                .retryWhen(retrySpec());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractProperties(Map<String, Object> body) {
        Object properties = body.get("properties");
        return properties instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
    }

    // The v3 API accepts only string property values; null would clear a property, so skip it.
    private Map<String, String> toHubSpotValues(Map<String, Object> properties) {
        Map<String, String> values = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if (value != null) {
                values.put(key, String.valueOf(value));
            }
        });
        return values;
    }

    private Retry retrySpec() {
        return Retry.backoff(properties.getMaxRetries(), Duration.ofSeconds(1))
                .filter(error -> error instanceof WebClientResponseException.TooManyRequests
                        || (error instanceof WebClientResponseException e && e.getStatusCode().is5xxServerError()))
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    private static final class NotFound extends RuntimeException {
        NotFound(Throwable cause) {
            super(cause);
        }
    }
}
