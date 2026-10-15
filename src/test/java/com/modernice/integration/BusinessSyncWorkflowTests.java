package com.modernice.integration;

import com.modernice.integration.connector.hubspot.MockSalesHubSpotClient;
import com.modernice.integration.connector.hubspot.dto.HubSpotObjectRequest;
import com.modernice.integration.connector.hubspot.mapper.HubSpotDealMapper;
import com.modernice.integration.connector.hubspot.mapper.HubSpotInvoiceMapper;
import com.modernice.integration.connector.hubspot.mapper.HubSpotLineItemMapper;
import com.modernice.integration.connector.hubspot.mapper.HubSpotOrderMapper;
import com.modernice.integration.connector.infor.business.MockInforBusinessClient;
import com.modernice.integration.connector.infor.business.mapper.InforBusinessMapper;
import com.modernice.integration.identity.BusinessIdentityService;
import com.modernice.integration.workflow.DealSyncWorkflow;
import com.modernice.integration.workflow.InvoiceSyncWorkflow;
import com.modernice.integration.workflow.OrderSyncWorkflow;
import com.modernice.integration.workflow.ProductSyncWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessSyncWorkflowTests {
    private MockInforBusinessClient infor;
    private MockSalesHubSpotClient hubSpot;
    private BusinessIdentityService identity;
    private InforBusinessMapper inforMapper;
    private OrderSyncWorkflow orderWorkflow;

    @BeforeEach
    void setUp() {
        infor = new MockInforBusinessClient();
        infor.seed();
        hubSpot = new MockSalesHubSpotClient();
        identity = mock(BusinessIdentityService.class);
        inforMapper = new InforBusinessMapper();
        orderWorkflow = new OrderSyncWorkflow(infor, inforMapper, new HubSpotOrderMapper(),
                new HubSpotLineItemMapper(), hubSpot, identity);
    }

    @Test
    void syncsOpportunityBothDirectionsAndPreservesUnmappedInforFields() {
        DealSyncWorkflow workflow = new DealSyncWorkflow(infor, inforMapper, new HubSpotDealMapper(),
                hubSpot, identity);

        workflow.syncInforOpportunityToHubSpot("OPP-1001").block();

        String dealId = infor.getOpportunity("OPP-1001").block().hubSpotDealId();
        assertThat(dealId).isNotBlank();
        assertThat(hubSpot.get("deals", dealId).block())
                .containsEntry("dealname", "Modern Ice Equipment Upgrade")
                .containsEntry("infor_opportunity_id", "OPP-1001");

        hubSpot.update("deals", dealId, new HubSpotObjectRequest(Map.of(
                "dealname", "Updated opportunity",
                "potential_amount", new BigDecimal("30000.00"),
                "actual_amount", new BigDecimal("1000.00"),
                "infor_probability", new BigDecimal("0.8")
        ))).block();
        when(identity.findSourceId("DEAL", "HUBSPOT", dealId)).thenReturn(Optional.of("OPP-1001"));

        workflow.syncHubSpotDealToInfor(dealId).block();

        assertThat(infor.getOpportunity("OPP-1001").block().opportunityName())
                .isEqualTo("Updated opportunity");
        assertThat(infor.getOpportunity("OPP-1001").block().potentialAmount())
                .isEqualByComparingTo("30000.00");
        assertThat(infor.getOpportunity("OPP-1001").block().stage()).isEqualTo("appointmentscheduled");
        assertThat(infor.getOpportunity("OPP-1001").block().hubSpotDealId()).isEqualTo(dealId);
    }

    @Test
    void syncsOrderAndAssociatesItsLineItems() {
        var result = orderWorkflow.syncOrderToHubSpot("ORD-1001").block();

        String orderId = infor.getOrder("ORD-1001").block().hubSpotOrderId();
        assertThat(result.hubSpotObjectId()).isEqualTo(orderId);
        assertThat(result.hubSpotObjectType()).isEqualTo("orders");
        assertThat(result.lineItemIds()).hasSize(1);
        assertThat(orderId).isNotBlank();
        assertThat(hubSpot.get("orders", orderId).block())
                .containsEntry("order_number", "ORD-1001");
        Map<String, Map<String, Object>> lines = hubSpot.snapshot("line_items");
        assertThat(lines).hasSize(1);
        assertThat(lines.values().iterator().next())
                .containsEntry("hs_sku", "SKU-ICE-01")
                .containsEntry("associated_order_id", orderId);
    }

    @Test
    void syncsInvoiceIntoTheExistingHubSpotOrderAndAssociatesInvoiceLines() {
        var orderResult = orderWorkflow.syncOrderToHubSpot("ORD-1001").block();
        String orderId = infor.getOrder("ORD-1001").block().hubSpotOrderId();
        InvoiceSyncWorkflow workflow = new InvoiceSyncWorkflow(infor, inforMapper,
                new HubSpotInvoiceMapper(), orderWorkflow, hubSpot, identity);

        var result = workflow.syncInvoiceToHubSpot("INV-1001").block();

        assertThat(result.hubSpotObjectId()).isEqualTo(orderId);
        assertThat(result.lineItemIds()).hasSize(1);
        assertThat(orderResult.hubSpotObjectId()).isEqualTo(orderId);
        assertThat(hubSpot.snapshot("orders")).hasSize(1);
        assertThat(hubSpot.get("orders", orderId).block())
                .containsEntry("invoice_number", "INV-1001")
                .containsEntry("invoiced_amount", new BigDecimal("2500.00"));
        assertThat(hubSpot.snapshot("line_items")).hasSize(2);
    }

    @Test
    void syncsInforProductAsHubSpotLineItem() {
        ProductSyncWorkflow workflow = new ProductSyncWorkflow(infor, inforMapper,
                new HubSpotLineItemMapper(), hubSpot, identity);

        var result = workflow.syncProductToHubSpotLineItem("SKU-ICE-01").block();

        assertThat(result.hubSpotObjectId()).isEqualTo("700000001");
        assertThat(result.hubSpotObjectType()).isEqualTo("line_items");
        assertThat(result.lineItemIds()).isEmpty();
        assertThat(infor.getProduct("SKU-ICE-01").block().hubSpotLineItemId())
                .isEqualTo(result.hubSpotObjectId());
        assertThat(hubSpot.snapshot("line_items")).hasSize(1);
        assertThat(hubSpot.snapshot("line_items").values().iterator().next())
                .containsEntry("hs_sku", "SKU-ICE-01")
                .containsEntry("name", "Commercial ice maker")
                .containsEntry("price", new BigDecimal("1250.00"));
    }

    @Test
    void productResyncReusesHubSpotIdStoredOnSourceRecord() {
        ProductSyncWorkflow workflow = new ProductSyncWorkflow(infor, inforMapper,
                new HubSpotLineItemMapper(), hubSpot, identity);

        var first = workflow.syncProductToHubSpotLineItem("SKU-ICE-01").block();
        var second = workflow.syncProductToHubSpotLineItem("SKU-ICE-01").block();

        assertThat(second.hubSpotObjectId()).isEqualTo(first.hubSpotObjectId());
        assertThat(hubSpot.snapshot("line_items")).hasSize(1);
        assertThat(infor.getProduct("SKU-ICE-01").block().hubSpotLineItemId())
                .isEqualTo(first.hubSpotObjectId());
    }

    @Test
    void mockHubSpotRejectsUnsupportedObjectTypes() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> hubSpot.get("companies", "id").block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported HubSpot business object type");
    }
}
