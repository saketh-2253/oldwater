package com.modernice.integration.connector.hubspot.mapper;

import com.modernice.integration.domain.model.Deal;
import com.modernice.integration.domain.model.Invoice;
import com.modernice.integration.domain.model.LineItem;
import com.modernice.integration.domain.model.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class HubSpotBusinessObjectMapperTests {

    private final HubSpotDealMapper dealMapper = new HubSpotDealMapper();
    private final HubSpotOrderMapper orderMapper = new HubSpotOrderMapper();
    private final HubSpotInvoiceMapper invoiceMapper = new HubSpotInvoiceMapper();
    private final HubSpotLineItemMapper lineItemMapper = new HubSpotLineItemMapper();

    @Test
    void mapsDealFieldsAndReadsHubSpotDateProperties() {
        Deal deal = new Deal(
                "OP-100", null, "Ice Machine", "A500", "Alex Manager",
                LocalDate.parse("2026-12-01"), LocalDate.parse("2026-11-10"),
                new BigDecimal("12000.00"), new BigDecimal("11500.00"), new BigDecimal("0.75"),
                "closedwon", "Won", "Price", "Referral", "Owner-1", "Equipment", "Description"
        );

        Map<String, Object> properties = dealMapper.toHubSpot(deal);

        assertEquals("OP-100", properties.get("infor_opportunity_id"));
        assertEquals("Ice Machine", properties.get("dealname"));
        assertEquals(new BigDecimal("12000.00"), properties.get("potential_amount"));
        assertEquals(LocalDate.parse("2026-12-01"), properties.get("est_close_date"));
        assertFalse(properties.containsKey("hubSpotDealId"));
        assertNull(properties.get("not_mapped"));

        Deal readBack = dealMapper.fromHubSpot(
                Map.of("dealname", "Updated name",
                        "potential_amount", "13000.50",
                        "infor_probability", "0.8",
                        "est_close_date", String.valueOf(Instant.parse("2026-12-01T00:00:00Z").toEpochMilli())),
                "HS-200"
        );

        assertEquals("HS-200", readBack.hubSpotDealId());
        assertEquals("Updated name", readBack.opportunityName());
        assertEquals(new BigDecimal("13000.50"), readBack.potentialAmount());
        assertEquals(new BigDecimal("0.8"), readBack.probability());
        assertNull(readBack.estimatedCloseDate());
        assertNull(readBack.status());
        assertNull(readBack.opportunityId());
    }

    @Test
    void mapsOrderToHubSpotOrderPropertiesAndOmitsHubSpotIdentityAndNestedItems() {
        LineItem lineItem = new LineItem("O-20", "01", "SKU-1", "Ice maker",
                new BigDecimal("250.00"), new BigDecimal("180.00"),
                new BigDecimal("70.00"), "Equipment", BigDecimal.ONE);
        Order order = new Order(
                "O-20", LocalDate.parse("2026-02-01"), "Standard", "open", "Example Customer",
                "C-20", "A500", new BigDecimal("2"), "PO-50", "JS", "Receiving", "West",
                LocalDate.parse("2026-02-05"), LocalDate.parse("2026-02-07"),
                new BigDecimal("500.00"), new BigDecimal("520.00"), new BigDecimal("500.00"),
                "ISR-1", "Inside Rep", "OSR-1", "Outside Rep", "Taken By",
                "RMA-1", "Order notes", "Customer notes", "HS-ORDER-1", List.of(lineItem)
        );

        Map<String, Object> properties = orderMapper.toHubSpot(order);

        assertEquals("O-20", properties.get("order_number"));
        assertEquals(LocalDate.parse("2026-02-07"), properties.get("invoice_date"));
        assertEquals(new BigDecimal("500.00"), properties.get("invoiced_amount"));
        assertFalse(properties.containsKey("hubSpotOrderId"));
        assertFalse(properties.containsKey("lineItems"));
    }

    @Test
    void mapsInvoiceOntoHubSpotOrderProperties() {
        Invoice invoice = new Invoice("I-300", "O-20", "A500", "Example Customer",
                LocalDate.parse("2026-02-07"), new BigDecimal("500.00"), List.of());

        Map<String, Object> properties = invoiceMapper.toHubSpotOrderProperties(invoice);

        assertEquals("I-300", properties.get("invoice_number"));
        assertEquals("O-20", properties.get("order_number"));
        assertEquals(LocalDate.parse("2026-02-07"), properties.get("invoice_date"));
        assertEquals(new BigDecimal("500.00"), properties.get("invoiced_amount"));
    }

    @Test
    void mapsLineItemPropertiesUsingHubSpotLineItemPropertyNames() {
        LineItem lineItem = new LineItem("O-20", "01", "SKU-1", "Ice maker",
                new BigDecimal("250.00"), new BigDecimal("180.00"),
                new BigDecimal("70.00"), "Equipment", new BigDecimal("2"));

        Map<String, Object> properties = lineItemMapper.toHubSpot(lineItem);

        assertEquals("SKU-1", properties.get("hs_sku"));
        assertEquals("Ice maker", properties.get("name"));
        assertEquals(new BigDecimal("250.00"), properties.get("price"));
        assertEquals(new BigDecimal("2"), properties.get("quantity"));
        assertEquals("O-20", properties.get("order_number"));
    }
}
