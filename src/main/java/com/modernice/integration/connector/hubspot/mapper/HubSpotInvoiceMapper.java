package com.modernice.integration.connector.hubspot.mapper;

import com.modernice.integration.domain.model.Invoice;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HubSpotInvoiceMapper {

    public Map<String, Object> toHubSpotOrderProperties(Invoice invoice) {
        Map<String, Object> properties = new LinkedHashMap<>();
        put(properties, "invoice_number", invoice.invoiceNumber());
        put(properties, "order_number", invoice.orderNumber());
        put(properties, "account_id", invoice.accountId());
        put(properties, "customer_name", invoice.customerName());
        put(properties, "invoice_date", invoice.invoiceDate());
        put(properties, "invoiced_amount", invoice.amount());
        return properties;
    }

    private static void put(Map<String, Object> properties, String name, Object value) {
        if (value != null) {
            properties.put(name, value);
        }
    }
}
