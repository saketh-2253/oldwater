package com.modernice.integration.connector.hubspot;

import com.modernice.integration.connector.hubspot.dto.HubSpotContactRequest;
import com.modernice.integration.connector.hubspot.dto.HubSpotCompanyRequest;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Profile("local")
public class MockHubSpotClient implements HubSpotClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockHubSpotClient.class);
    private final Map<String, Map<String, Object>> contacts = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> companies = new ConcurrentHashMap<>();
    private final AtomicLong nextContactId = new AtomicLong(987654320);
    private final AtomicLong nextCompanyId = new AtomicLong(500000000);

    @Override
    public Mono<Map<String, Object>> getContact(String hubSpotContactId) {
        return Mono.justOrEmpty(contacts.get(hubSpotContactId))
                .map(existing -> new ConcurrentHashMap<>(existing));
    }

    @Override
    public Mono<String> createContact(HubSpotContactRequest request) {
        return Mono.fromSupplier(() -> {
            String hubSpotContactId = Long.toString(nextContactId.incrementAndGet());
            contacts.put(hubSpotContactId, new ConcurrentHashMap<>(request.properties()));
            LOGGER.info("HubSpot mock created contact {}: {}", hubSpotContactId, request.properties());
            return hubSpotContactId;
        });
    }

    @Override
    public Mono<Void> upsertContact(String hubSpotContactId, HubSpotContactRequest request) {
        return Mono.fromRunnable(() -> {
            boolean created = !contacts.containsKey(hubSpotContactId);
            Map<String, Object> properties = contacts.computeIfAbsent(
                    hubSpotContactId,
                    ignored -> new ConcurrentHashMap<>()
            );

            synchronized (properties) {
                properties.putAll(request.properties());
            }

            if (created) {
                LOGGER.info("HubSpot mock created contact {}: {}", hubSpotContactId, request.properties());
            } else {
                LOGGER.info("HubSpot mock upserted contact {} with properties: {}", hubSpotContactId, request.properties());
            }
        });
    }

    @Override
    public Mono<Void> updateContact(String hubSpotContactId, HubSpotContactRequest request) {
        return Mono.fromRunnable(() -> {
            Map<String, Object> properties = contacts.computeIfAbsent(
                    hubSpotContactId,
                    ignored -> new ConcurrentHashMap<>()
            );

            synchronized (properties) {
                Map<String, Object> changedProperties = new LinkedHashMap<>();
                request.properties().forEach((key, value) -> {
                    if (!Objects.equals(properties.get(key), value)) {
                        changedProperties.put(key, value);
                    }
                });

                if (changedProperties.isEmpty()) {
                    LOGGER.info("HubSpot mock contact {} is already up to date; no update needed", hubSpotContactId);
                    return;
                }

                properties.putAll(changedProperties);
                LOGGER.info("HubSpot mock updated contact {} with changed properties: {}", hubSpotContactId, changedProperties);
            }
        });
    }

    @Override
    public Mono<Map<String, Object>> getCompany(String hubSpotCompanyId) {
        return Mono.justOrEmpty(companies.get(hubSpotCompanyId))
                .map(existing -> new ConcurrentHashMap<>(existing));
    }

    @Override
    public Mono<String> createCompany(HubSpotCompanyRequest request) {
        return Mono.fromSupplier(() -> {
            String hubSpotCompanyId = Long.toString(nextCompanyId.incrementAndGet());
            companies.put(hubSpotCompanyId, new ConcurrentHashMap<>(request.properties()));
            LOGGER.info("HubSpot mock created company {}: {}", hubSpotCompanyId, request.properties());
            return hubSpotCompanyId;
        });
    }

    @Override
    public Mono<Void> updateCompany(String hubSpotCompanyId, HubSpotCompanyRequest request) {
        return Mono.fromRunnable(() -> {
            Map<String, Object> properties = companies.computeIfAbsent(
                    hubSpotCompanyId,
                    ignored -> new ConcurrentHashMap<>()
            );
            synchronized (properties) {
                Map<String, Object> changedProperties = new LinkedHashMap<>();
                request.properties().forEach((key, value) -> {
                    if (!Objects.equals(properties.get(key), value)) {
                        changedProperties.put(key, value);
                    }
                });
                if (changedProperties.isEmpty()) {
                    LOGGER.info("HubSpot mock company {} is already up to date; no update needed", hubSpotCompanyId);
                    return;
                }
                properties.putAll(changedProperties);
                LOGGER.info("HubSpot mock updated company {} with changed properties: {}",
                        hubSpotCompanyId, changedProperties);
            }
        });
    }
}
