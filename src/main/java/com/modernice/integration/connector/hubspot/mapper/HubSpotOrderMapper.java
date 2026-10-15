package com.modernice.integration.connector.hubspot.mapper;

import com.modernice.integration.domain.model.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HubSpotOrderMapper {

    public Map<String, Object> toHubSpot(Order order) {
        Map<String, Object> properties = new LinkedHashMap<>();
        put(properties, "entered_date", order.enteredDate());
        put(properties, "order_number", order.orderNumber());
        put(properties, "transaction_type", order.transactionType());
        put(properties, "hs_pipeline_stage", order.stage());
        put(properties, "customer_name", order.customerName());
        put(properties, "quantity_ordered", order.quantityOrdered());
        put(properties, "reference", order.reference());
        put(properties, "taken_by", order.takenBy());
        put(properties, "customer_number", order.customerNumber());
        put(properties, "ship_to_name", order.shipToName());
        put(properties, "division", order.division());
        put(properties, "shipped_date", order.shippedDate());
        put(properties, "invoice_date", order.invoicedDate());
        put(properties, "invoiced_amount", order.invoicedAmount());
        put(properties, "ordered_amount", order.orderedAmount());
        put(properties, "shipped_amount", order.shippedAmount());
        put(properties, "inside_sales_rep", order.insideSalesRep());
        put(properties, "inside_sales_rep_name", order.insideSalesRepName());
        put(properties, "outside_sales_rep", order.outsideSalesRep());
        put(properties, "outside_sales_rep_name", order.outsideSalesRepName());
        put(properties, "taken_by_name", order.takenByName());
        put(properties, "vendor_return_authorization", order.vendorReturnAuthorization());
        put(properties, "order_notes", order.orderNotes());
        put(properties, "customer_notes", order.customerNotes());
        return properties;
    }

    private static void put(Map<String, Object> properties, String name, Object value) {
        if (value != null) {
            properties.put(name, value);
        }
    }
}
