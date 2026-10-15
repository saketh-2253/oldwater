package com.modernice.integration.connector.hubspot.mapper;

import com.modernice.integration.domain.model.LineItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HubSpotLineItemMapper {

    public Map<String, Object> toHubSpot(LineItem lineItem) {
        Map<String, Object> properties = new LinkedHashMap<>();
        put(properties, "order_suffix", lineItem.orderSuffix());
        put(properties, "hs_sku", lineItem.productSku());
        put(properties, "name", lineItem.name());
        put(properties, "price", lineItem.unitPrice());
        put(properties, "unit_cost", lineItem.unitCost());
        put(properties, "profit", lineItem.profit());
        put(properties, "line_prodcat", lineItem.productCategory());
        put(properties, "order_number", lineItem.orderNumber());
        put(properties, "quantity", lineItem.quantity());
        return properties;
    }

    private static void put(Map<String, Object> properties, String name, Object value) {
        if (value != null) {
            properties.put(name, value);
        }
    }
}
