package com.modernice.integration.connector.hubspot;

import com.modernice.integration.connector.hubspot.dto.HubSpotObjectRequest;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface SalesHubSpotClient {
    Mono<Map<String, Object>> get(String objectType, String hubSpotId);
    Mono<String> create(String objectType, HubSpotObjectRequest request);
    Mono<Void> update(String objectType, String hubSpotId, HubSpotObjectRequest request);
    Mono<Void> associateLineItemWithOrder(String lineItemId, String orderId);
}
