package com.modernice.integration.connector.infor.business;

import com.modernice.integration.connector.infor.business.dto.InforInvoiceDto;
import com.modernice.integration.connector.infor.business.dto.InforLineItemDto;
import com.modernice.integration.connector.infor.business.dto.InforOpportunityDto;
import com.modernice.integration.connector.infor.business.dto.InforOrderDto;
import com.modernice.integration.connector.infor.business.dto.InforProductDto;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Profile("local")
public class MockInforBusinessClient implements InforBusinessClient {
    private final ConcurrentMap<String, InforOpportunityDto> opportunities = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, InforOrderDto> orders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, InforInvoiceDto> invoices = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, InforProductDto> products = new ConcurrentHashMap<>();

    @PostConstruct
    public void seed() {
        opportunities.putIfAbsent("OPP-1001", new InforOpportunityDto(
                "OPP-1001", null, "Modern Ice Equipment Upgrade", "A500", "U738UA00000M",
                LocalDate.parse("2026-12-31"), null, new BigDecimal("25000.00"), BigDecimal.ZERO,
                new BigDecimal("0.50"), "appointmentscheduled", "Open", null, "Referral",
                "U738UA00002W", "Equipment - Standard", "Local sample opportunity"));
        InforLineItemDto sampleLine = new InforLineItemDto("ORD-1001", "01", "SKU-ICE-01",
                "Commercial ice maker", new BigDecimal("1250.00"), new BigDecimal("900.00"),
                new BigDecimal("350.00"), "Equipment", new BigDecimal("2"));
        orders.putIfAbsent("ORD-1001", new InforOrderDto(
                "ORD-1001", null, LocalDate.parse("2026-10-01"), "Standard", "open",
                "Modern Ice Customer", "12345", "A500", new BigDecimal("2"), "PO-1001", "U738UA00000M",
                "Modern Ice Customer", "OH", null, null, null, new BigDecimal("2500.00"),
                null, null, null, null, null, null, null, "Local sample order", null,
                List.of(sampleLine)));
        invoices.putIfAbsent("INV-1001", new InforInvoiceDto("INV-1001", "ORD-1001", "A500",
                "Modern Ice Customer", LocalDate.parse("2026-10-04"), new BigDecimal("2500.00"),
                List.of(sampleLine)));
        products.putIfAbsent("SKU-ICE-01", new InforProductDto("SKU-ICE-01", "Commercial ice maker",
                new BigDecimal("1250.00"), new BigDecimal("900.00"), new BigDecimal("350.00"), "Equipment", null));
    }

    @Override
    public Flux<InforOpportunityDto> listOpportunities() {
        return Flux.fromIterable(opportunities.values());
    }

    @Override
    public Mono<InforOpportunityDto> getOpportunity(String id) {
        return Mono.justOrEmpty(opportunities.get(id));
    }

    @Override
    public Mono<Void> upsertOpportunity(InforOpportunityDto value) {
        return Mono.fromRunnable(() -> opportunities.compute(value.opportunityId(), (id, current) ->
                current != null && value.hubSpotDealId() == null
                        ? new InforOpportunityDto(value.opportunityId(), current.hubSpotDealId(),
                        value.opportunityName(), value.accountId(), value.accountManager(), value.estimatedCloseDate(),
                        value.actualCloseDate(), value.potentialAmount(), value.actualAmount(), value.probability(),
                        value.stage(), value.status(), value.reasonWon(), value.leadSource(), value.owner(),
                        value.opportunityType(), value.description(), value.contactIds(), value.productSkus(),
                        value.salesOrderIds())
                        : value));
    }

    @Override
    public Flux<InforOrderDto> listOrders() {
        return Flux.fromIterable(orders.values());
    }

    @Override
    public Mono<InforOrderDto> getOrder(String id) {
        return Mono.justOrEmpty(orders.get(id));
    }

    @Override
    public Mono<Void> upsertOrder(InforOrderDto value) {
        return Mono.fromRunnable(() -> orders.compute(value.orderNumber(), (id, current) ->
                current != null && value.hubSpotOrderId() == null
                        ? new InforOrderDto(value.orderNumber(), current.hubSpotOrderId(), value.enteredDate(),
                        value.transactionType(), value.stage(), value.customerName(), value.customerNumber(),
                        value.accountId(), value.quantityOrdered(), value.reference(), value.takenBy(), value.shipToName(),
                        value.division(), value.shippedDate(), value.invoicedDate(), value.invoicedAmount(),
                        value.orderedAmount(), value.shippedAmount(), value.insideSalesRep(),
                        value.insideSalesRepName(), value.outsideSalesRep(), value.outsideSalesRepName(),
                        value.takenByName(), value.vendorReturnAuthorization(), value.orderNotes(),
                        value.customerNotes(), value.lineItems())
                        : value));
    }

    @Override
    public Flux<InforInvoiceDto> listInvoices() {
        return Flux.fromIterable(invoices.values());
    }

    @Override
    public Mono<InforInvoiceDto> getInvoice(String id) {
        return Mono.justOrEmpty(invoices.get(id));
    }

    @Override
    public Mono<Void> upsertInvoice(InforInvoiceDto value) {
        return Mono.fromRunnable(() -> invoices.put(value.invoiceNumber(), value));
    }

    @Override
    public Flux<InforProductDto> listProducts() {
        return Flux.fromIterable(products.values());
    }

    @Override
    public Mono<InforProductDto> getProduct(String id) {
        return Mono.justOrEmpty(products.get(id));
    }

    @Override
    public Mono<Void> upsertProduct(InforProductDto value) {
        return Mono.fromRunnable(() -> products.compute(value.productSku(), (productSku, current) ->
                current != null && value.hubSpotLineItemId() == null
                        ? new InforProductDto(value.productSku(), value.name(), value.unitPrice(), value.unitCost(),
                        value.profit(), value.productCategory(), current.hubSpotLineItemId())
                        : value));
    }
}
