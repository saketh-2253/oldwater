package com.modernice.integration.connector.hubspot;

import com.modernice.integration.connector.hubspot.dto.HubSpotObjectRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Profile("local")
public class MockSalesHubSpotClient implements SalesHubSpotClient {
    private final Map<String, Map<String, Map<String, Object>>> objects = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(700000000);

    @Override
    public Mono<Map<String, Object>> get(String objectType, String hubSpotId) {
        return Mono.justOrEmpty(store(objectType).get(hubSpotId))
                .map(LinkedHashMap::new);
    }

    @Override
    public Mono<String> create(String objectType, HubSpotObjectRequest request) {
        return Mono.fromSupplier(() -> {
            String id = Long.toString(nextId.incrementAndGet());
            store(objectType).put(id, new ConcurrentHashMap<>(request.properties()));
            return id;
        });
    }

    @Override
    public Mono<Void> update(String objectType, String hubSpotId, HubSpotObjectRequest request) {
        return Mono.fromRunnable(() -> store(objectType)
                .computeIfAbsent(hubSpotId, ignored -> new ConcurrentHashMap<>())
                .putAll(request.properties()));
    }

    @Override
    public Mono<Void> associateLineItemWithOrder(String lineItemId, String orderId) {
        return Mono.fromRunnable(() -> {
            Map<String, Object> lineItem = store("line_items").get(lineItemId);
            if (lineItem == null) {
                throw new IllegalStateException("HubSpot line item not found: " + lineItemId);
            }
            lineItem.put("associated_order_id", orderId);
        });
    }

    public Map<String, Map<String, Object>> snapshot(String objectType) {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        store(objectType).forEach((id, properties) -> copy.put(id, new LinkedHashMap<>(properties)));
        return copy;
    }

    private Map<String, Map<String, Object>> store(String objectType) {
        return objects.computeIfAbsent(normalize(objectType), ignored -> new ConcurrentHashMap<>());
    }

    private String normalize(String objectType) {
        return switch (objectType) {
            case "deal", "deals" -> "deals";
            case "order", "orders" -> "orders";
            case "line_item", "line_items" -> "line_items";
            default -> throw new IllegalArgumentException("Unsupported HubSpot business object type: " + objectType);
        };
    }
}
