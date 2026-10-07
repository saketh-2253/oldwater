package com.modernice.integration.connector.hubspot;

import com.modernice.integration.connector.hubspot.dto.HubSpotContactRequest;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface HubSpotClient {
    Mono<Map<String, Object>> getContact(String hubSpotContactId);
    Mono<String> createContact(HubSpotContactRequest request);
    Mono<Void> updateContact(String hubSpotContactId, HubSpotContactRequest request);
    Mono<Void> upsertContact(String hubSpotContactId, HubSpotContactRequest request);
}
