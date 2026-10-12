package com.modernice.integration.connector.infor.crm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Primary
public class SDataInforCrmClient implements InforCrmClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final MediaType SDATA_JSON =
            MediaType.parseMediaType("application/json;vnd.sage=sdata");

    private final WebClient webClient;
    private final InforSDataProperties properties;
    private final InforSDataMapper mapper;
    private final SDataAuthenticationSelector authenticationSelector;
    private final ObjectMapper objectMapper;

    public SDataInforCrmClient(
            WebClient.Builder webClientBuilder,
            InforSDataProperties properties,
            InforSDataMapper mapper,
            SDataAuthenticationSelector authenticationSelector,
            ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl(trimTrailingSlash(properties.getBaseUrl()) + "/").build();
        this.properties = properties;
        this.mapper = mapper;
        this.authenticationSelector = authenticationSelector;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<InforContactDto> getContact(String crmContactId) {
        return getResource("contacts", crmContactId)
                .flatMap(contact -> Mono.zip(
                                resolveLinkedResource(contact, "Address", "addresses")
                                        .defaultIfEmpty(Map.of()),
                                resolveLinkedResource(contact, "Account", "accounts")
                                        .defaultIfEmpty(Map.of())
                        )
                        .map(related -> mapper.fromSDataContact(
                                crmContactId,
                                contact,
                                related.getT1(),
                                related.getT2()
                        )));
    }

    @Override
    public Mono<Void> setContact(InforContactDto contact) {
        return upsertContact(contact);
    }

    @Override
    public Mono<Void> upsertContact(InforContactDto contact) {
        return putResourceWithAddress(
                "contacts",
                contact.crmContactId(),
                mapper.toSDataContact(contact)
        );
    }

    @Override
    public Mono<Void> updateHubSpotRecordId(String crmContactId, String hubSpotRecordId) {
        return putResource("contacts", crmContactId, Map.of(
                properties.getFields().getContactHubSpotId(), hubSpotRecordId
        ));
    }

    @Override
    public Mono<InforAccountDto> getAccount(String accountId) {
        return getResource("accounts", accountId)
                .flatMap(account -> resolveLinkedResource(account, "Address", "addresses")
                        .map(address -> mapper.fromSDataAccount(accountId, account, address))
                        .switchIfEmpty(Mono.fromSupplier(
                                () -> mapper.fromSDataAccount(accountId, account, null)
                        )));
    }

    @Override
    public Mono<Void> upsertAccount(InforAccountDto account) {
        return putResourceWithAddress(
                "accounts",
                account.accountId(),
                mapper.toSDataAccount(account)
        );
    }

    @Override
    public Mono<Void> updateHubSpotCompanyId(String accountId, String hubSpotCompanyId) {
        return putResource("accounts", accountId, Map.of(
                properties.getFields().getAccountHubSpotId(), hubSpotCompanyId
        ));
    }

    private Mono<Map<String, Object>> getResource(String collection, String key) {
        return webClient.get()
                .uri(resourcePath(collection, key))
                .headers(authenticationSelector::apply)
                .accept(SDATA_JSON)
                .exchangeToMono(response -> {
                    if (response.statusCode().value() == 404) {
                        return response.releaseBody().then(Mono.empty());
                    }
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.bodyToMono(MAP_TYPE)
                            .map(this::normalizeResource);
                });
    }

    private Mono<Void> putResource(String collection, String key, Map<String, Object> body) {
        return webClient.put()
                .uri(resourcePath(collection, key))
                .headers(authenticationSelector::apply)
                .accept(SDATA_JSON)
                .contentType(SDATA_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.createException().flatMap(Mono::error);
                    }
                    return response.releaseBody();
                });
    }

    private Mono<Void> putResourceWithAddress(
            String collection,
            String key,
            Map<String, Object> properties) {
        Map<String, Object> address = InforSDataMapper.nested(properties, "Address");
        if (address == null || address.isEmpty()) {
            return putResource(collection, key, properties);
        }

        return getResource(collection, key)
                .map(existing -> Optional.ofNullable(
                        InforSDataMapper.referenceKey(existing.get("Address"))
                ))
                .defaultIfEmpty(Optional.empty())
                .flatMap(addressKey -> {
                    if (addressKey.isEmpty()) {
                        return putResource(collection, key, properties);
                    }
                    Map<String, Object> parentProperties = new LinkedHashMap<>(properties);
                    parentProperties.remove("Address");
                    return putResource(collection, key, parentProperties)
                            .then(putResource("addresses", addressKey.get(), address));
                });
    }

    private Mono<Map<String, Object>> resolveLinkedResource(
            Map<String, Object> resource,
            String relationName,
            String collection) {
        Map<String, Object> relation = InforSDataMapper.nested(resource, relationName);
        if (relation == null) {
            return Mono.empty();
        }
        if (relation.keySet().stream().anyMatch(key -> !key.startsWith("$"))) {
            return Mono.just(relation);
        }
        String key = InforSDataMapper.referenceKey(relation);
        return key == null ? Mono.just(relation) : getResource(collection, key);
    }

    private Map<String, Object> normalizeResource(Map<String, Object> response) {
        Object entry = response.get("entry");
        if (entry instanceof Map<?, ?>) {
            return objectMapper.convertValue(entry, new TypeReference<>() {});
        }
        return response;
    }

    private String resourcePath(String collection, String key) {
        String encodedKey = UriUtils.encodePathSegment(key, StandardCharsets.UTF_8);
        return collection + "('" + encodedKey + "')?format=json";
    }

    private static String trimTrailingSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("integration.infor.sdata.base-url must be configured");
        }
        int end = baseUrl.length();
        while (end > 0 && baseUrl.charAt(end - 1) == '/') {
            end--;
        }
        return baseUrl.substring(0, end);
    }
}
