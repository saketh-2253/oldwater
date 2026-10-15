package com.modernice.integration.connector.hubspot;

import com.modernice.integration.connector.hubspot.dto.HubSpotObjectRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Profile("!local")
public class RealSalesHubSpotClient implements SalesHubSpotClient {
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private final WebClient webClient;

    public RealSalesHubSpotClient(WebClient.Builder builder, HubSpotProperties properties) {
        if (!StringUtils.hasText(properties.getAccessToken())) {
            throw new IllegalStateException("HUBSPOT_ACCESS_TOKEN must be configured outside the local profile");
        }
        this.webClient = builder.baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Mono<Map<String, Object>> get(String objectType, String hubSpotId) {
        return webClient.get().uri(path(objectType) + "/{id}", hubSpotId).retrieve()
                .bodyToMono(MAP_TYPE)
                .map(body -> body.get("properties") instanceof Map<?, ?> values
                        ? copyProperties(values) : new LinkedHashMap<String, Object>())
                .onErrorResume(WebClientResponseException.NotFound.class, ignored -> Mono.empty());
    }

    @Override
    public Mono<String> create(String objectType, HubSpotObjectRequest request) {
        return webClient.post().uri(path(objectType)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("properties", stringProperties(request.properties())))
                .retrieve().bodyToMono(MAP_TYPE).map(body -> String.valueOf(body.get("id")));
    }

    @Override
    public Mono<Void> update(String objectType, String hubSpotId, HubSpotObjectRequest request) {
        return webClient.patch().uri(path(objectType) + "/{id}", hubSpotId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("properties", stringProperties(request.properties())))
                .retrieve().toBodilessEntity().then();
    }

    @Override
    public Mono<Void> associateLineItemWithOrder(String lineItemId, String orderId) {
        return Mono.error(new IllegalStateException(
                "Real HubSpot line-item/order association is not configured: confirm the target Order object "
                        + "and association type in the HubSpot portal before implementing this API call."));
    }

    private String path(String objectType) {
        return "/crm/v3/objects/" + switch (objectType) {
            case "deal", "deals" -> "deals";
            case "order", "orders" -> "orders";
            case "line_item", "line_items" -> "line_items";
            default -> throw new IllegalArgumentException("Unsupported HubSpot business object type: " + objectType);
        };
    }

    private Map<String, String> stringProperties(Map<String, Object> properties) {
        Map<String, String> values = new LinkedHashMap<>();
        properties.forEach((key, value) -> {
            if (value != null) {
                String hubSpotValue = value instanceof LocalDate date
                        ? Long.toString(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                        : String.valueOf(value);
                values.put(key, hubSpotValue);
            }
        });
        return values;
    }

    private Map<String, Object> copyProperties(Map<?, ?> source) {
        Map<String, Object> values = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key instanceof String name) {
                values.put(name, value);
            }
        });
        return values;
    }
}
