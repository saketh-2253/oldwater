package com.modernice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernice.integration.connector.infor.business.MockInforBusinessClient;
import com.modernice.integration.connector.infor.crm.InforSDataMapper;
import com.modernice.integration.connector.infor.crm.InforSDataProperties;
import com.modernice.integration.connector.infor.crm.MockInforCrmClient;
import com.modernice.integration.controller.MockInforBusinessSDataController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockInforBusinessSDataControllerTests {
    private MockInforBusinessClient client;
    private MockInforCrmClient crmClient;
    private MockInforBusinessSDataController controller;
    private ServerHttpRequest request;

    @BeforeEach
    void setUp() {
        client = new MockInforBusinessClient();
        client.seed();
        crmClient = new MockInforCrmClient();
        crmClient.initializeDefaultContacts();
        controller = new MockInforBusinessSDataController(client, crmClient,
                new InforSDataMapper(new InforSDataProperties()),
                new ObjectMapper().findAndRegisterModules());
        request = MockServerHttpRequest.get(
                "http://localhost:8081/mock/infor-sdata/sdata/slx/dynamic/-/opportunities?format=json").build();
    }

    @Test
    void createsAndListsOpportunityWithoutReplacingSeededRecord() {
        Map<String, Object> created = controller.createOpportunity(Map.of(
                "opportunityName", "New Postman Opportunity",
                "Account", Map.of("$key", "A500"),
                "potentialAmount", new BigDecimal("42000.00"),
                "estimatedCloseDate", "2026-12-31"
        ), request).block();

        String id = String.valueOf(created.get("$key"));
        assertThat(id).startsWith("OPP-MOCK-");
        assertThat(controller.getOpportunity(id, request).block())
                .containsEntry("opportunityName", "New Postman Opportunity")
                .containsEntry("$url", "http://localhost:8081/mock/infor-sdata/sdata/slx/dynamic/-/opportunities('"
                        + id + "')")
                .containsEntry("Account", Map.of(
                        "$key", "A500",
                        "$url", "http://localhost:8081/mock/infor-sdata/sdata/slx/dynamic/-/accounts('A500')",
                        "$uuid", "00000000-0000-0000-0000-000000000000",
                        "$lookup", "http://localhost:8081/mock/infor-sdata/sdata/slx/dynamic/-/accounts?format=json"))
                .doesNotContainKey("accountId");
        assertThat(controller.listOpportunities(1, 50, request).block().get("$resources"))
                .asList().hasSize(2);
        assertThat(client.getOpportunity(id).block().accountId()).isEqualTo("A500");
        assertThat(client.getOpportunity("OPP-1001").block()).isNotNull();
    }

    @Test
    void createsOpportunityWithContactProductAndSalesOrderAssociations() {
        Map<String, Object> created = controller.createOpportunity(Map.of(
                "opportunityName", "Related opportunity",
                "Contacts", List.of(Map.of("$key", "103")),
                "Products", List.of(Map.of("$key", "SKU-ICE-01")),
                "SalesOrders", List.of(Map.of("$key", "ORD-1001"))
        ), request).block();
        String id = String.valueOf(created.get("$key"));

        assertThat(created.get("Contacts")).isEqualTo(Map.of(
                "$url", "http://localhost:8081/mock/infor-sdata/sdata/slx/dynamic/-/opportunities('" + id + "')/Contacts"));
        assertThat(controller.listOpportunityContacts(id, 1, 50, request).block().get("$resources"))
                .asList().extracting("$key").containsExactly("103");
        assertThat(controller.listOpportunityProducts(id, 1, 50, request).block().get("$resources"))
                .asList().extracting("$key").containsExactly("SKU-ICE-01");
        assertThat(controller.listOpportunitySalesOrders(id, 1, 50, request).block().get("$resources"))
                .asList().extracting("$key").containsExactly("ORD-1001");
        assertThat(client.getOpportunity(id).block().contactIds()).containsExactly("103");
        assertThat(client.getOpportunity(id).block().productSkus()).containsExactly("SKU-ICE-01");
        assertThat(client.getOpportunity(id).block().salesOrderIds()).containsExactly("ORD-1001");
    }

    @Test
    void partiallyUpdatesExistingOrderAndPreservesHubSpotIdAndOtherFields() {
        client.upsertOrder(new com.modernice.integration.connector.infor.business.dto.InforOrderDto(
                "ORD-EDIT", "HS-ORDER-1", LocalDate.parse("2026-10-01"), "Standard", "open",
                "Original customer", "C-1", "A500", BigDecimal.ONE, null, null, null, null,
                null, null, null, new BigDecimal("100.00"), null, null, null, null, null,
                null, null, "Existing note", null, List.of()
        )).block();

        Map<String, Object> updated = controller.updateOrder("ORD-EDIT", Map.of(
                "stage", "shipped",
                "customerName", "Updated customer"
        ), request).block();

        assertThat(updated)
                .containsEntry("stage", "shipped")
                .containsEntry("customerName", "Updated customer")
                .containsEntry("hubSpotOrderId", "HS-ORDER-1")
                .containsEntry("orderNotes", "Existing note");
        assertThat(controller.listOrders(1, 50, request).block().get("$resources")).asList().hasSize(2);
    }

    @Test
    void orderLineItemsExposeProductSDataReferences() {
        Map<String, Object> created = controller.createOrder(Map.of(
                "customerName", "Linked customer",
                "customerNumber", "12345",
                "Account", Map.of("$key", "A500"),
                "lineItems", List.of(Map.of(
                        "orderSuffix", "01",
                        "name", "Commercial ice maker",
                        "unitPrice", new BigDecimal("1250.00"),
                        "quantity", BigDecimal.ONE,
                        "Product", Map.of("$key", "SKU-ICE-01")
                ))
        ), request).block();

        @SuppressWarnings("unchecked")
        Map<String, Object> account = (Map<String, Object>) created.get("Account");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) created.get("lineItems");
        @SuppressWarnings("unchecked")
        Map<String, Object> product = (Map<String, Object>) lineItems.get(0).get("Product");

        assertThat(account).containsEntry("$key", "A500")
                .containsEntry("$url", "http://localhost:8081/mock/infor-sdata/sdata/slx/dynamic/-/accounts('A500')");
        assertThat(product).containsEntry("$key", "SKU-ICE-01")
                .containsEntry("$url", "http://localhost:8081/mock/infor-sdata/sdata/slx/dynamic/-/products('SKU-ICE-01')");
        assertThat(client.getOrder(String.valueOf(created.get("$key"))).block().accountId()).isEqualTo("A500");
        assertThat(client.getOrder(String.valueOf(created.get("$key"))).block().lineItems().get(0).productSku())
                .isEqualTo("SKU-ICE-01");
    }

    @Test
    void createsAndPartiallyUpdatesInvoice() {
        Map<String, Object> created = controller.createInvoice(Map.of(
                "orderNumber", "ORD-1001",
                "accountId", "A500",
                "customerName", "New invoice customer",
                "invoiceDate", "2026-10-14",
                "amount", new BigDecimal("725.00"),
                "Account", Map.of("$key", "A500"),
                "SalesOrder", Map.of("$key", "ORD-1001")
        ), request).block();
        String id = String.valueOf(created.get("$key"));

        Map<String, Object> updated = controller.updateInvoice(id, Map.of(
                "amount", new BigDecimal("900.00")
        ), request).block();

        assertThat(updated.get("invoiceNumber")).isEqualTo(id);
        assertThat((BigDecimal) updated.get("amount")).isEqualByComparingTo("900.00");
        assertThat(updated.get("customerName")).isEqualTo("New invoice customer");
        assertThat(updated).containsKeys("Account", "SalesOrder")
                .doesNotContainKeys("accountId", "orderNumber");
        assertThat(controller.listInvoices(1, 50, request).block().get("$resources")).asList().hasSize(2);
    }

    @Test
    void createsAndPartiallyUpdatesProductPreservingHubSpotLineItemId() {
        Map<String, Object> created = controller.createProduct(Map.of(
                "name", "Postman freezer",
                "unitPrice", new BigDecimal("2100.00"),
                "unitCost", new BigDecimal("1500.00"),
                "profit", new BigDecimal("600.00"),
                "productCategory", "Equipment"
        ), request).block();
        String sku = String.valueOf(created.get("$key"));
        client.upsertProduct(new com.modernice.integration.connector.infor.business.dto.InforProductDto(
                "SKU-ICE-01", "Commercial ice maker", new BigDecimal("1250.00"),
                new BigDecimal("900.00"), new BigDecimal("350.00"), "Equipment", "HS-LINE-1"
        )).block();

        Map<String, Object> updated = controller.updateProduct("SKU-ICE-01", Map.of(
                "unitPrice", new BigDecimal("1350.00")
        ), request).block();

        assertThat(created.get("productSku")).isEqualTo(sku);
        assertThat((BigDecimal) updated.get("unitPrice")).isEqualByComparingTo("1350.00");
        assertThat(updated)
                .containsEntry("name", "Commercial ice maker")
                .containsEntry("hubSpotLineItemId", "HS-LINE-1");
        assertThat(controller.listProducts(1, 50, request).block().get("$resources")).asList().hasSize(2);
    }

    @Test
    void missingBusinessRecordReturnsNotFound() {
        assertThatThrownBy(() -> controller.getInvoice("missing", request).block())
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
