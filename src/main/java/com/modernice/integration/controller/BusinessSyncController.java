package com.modernice.integration.controller;

import com.modernice.integration.connector.hubspot.SalesHubSpotClient;
import com.modernice.integration.connector.hubspot.dto.HubSpotObjectRequest;
import com.modernice.integration.connector.infor.business.InforBusinessClient;
import com.modernice.integration.connector.infor.business.dto.InforInvoiceDto;
import com.modernice.integration.connector.infor.business.dto.InforOpportunityDto;
import com.modernice.integration.connector.infor.business.dto.InforOrderDto;
import com.modernice.integration.connector.infor.business.dto.InforProductDto;
import com.modernice.integration.workflow.DealSyncWorkflow;
import com.modernice.integration.workflow.BusinessSyncResult;
import com.modernice.integration.workflow.InvoiceSyncWorkflow;
import com.modernice.integration.workflow.OrderSyncWorkflow;
import com.modernice.integration.workflow.ProductSyncWorkflow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BusinessSyncController {
    private final InforBusinessClient inforClient;
    private final SalesHubSpotClient hubSpotClient;
    private final DealSyncWorkflow dealWorkflow;
    private final OrderSyncWorkflow orderWorkflow;
    private final InvoiceSyncWorkflow invoiceWorkflow;
    private final ProductSyncWorkflow productWorkflow;
    private final ObjectMapper objectMapper;

    @GetMapping("/infor/opportunities")
    public Flux<Map<String, Object>> listOpportunities() {
        return inforClient.listOpportunities().map(value -> sdataResource(value.opportunityId(), "opportunities", value));
    }

    @GetMapping("/infor/opportunities/{id}")
    public Mono<InforOpportunityDto> getOpportunity(@PathVariable String id) {
        return required(inforClient.getOpportunity(id), "INFOR opportunity", id);
    }

    @PostMapping("/infor/opportunities")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createOpportunity(@RequestBody Map<String, Object> body) {
        InforOpportunityDto value = convert(body, InforOpportunityDto.class);
        String id = "OPP-MOCK-" + UUID.randomUUID();
        InforOpportunityDto created = new InforOpportunityDto(id, value.hubSpotDealId(), value.opportunityName(),
                value.accountId(), value.accountManager(), value.estimatedCloseDate(), value.actualCloseDate(),
                value.potentialAmount(), value.actualAmount(), value.probability(), value.stage(), value.status(),
                value.reasonWon(), value.leadSource(), value.owner(), value.opportunityType(), value.description(),
                value.contactIds(), value.productSkus(), value.salesOrderIds());
        return inforClient.upsertOpportunity(created)
                .thenReturn(sdataResource(id, "opportunities", created));
    }

    @PutMapping("/infor/opportunities/{id}")
    public Mono<Map<String, Object>> putOpportunity(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return inforClient.getOpportunity(id)
                .map(current -> merge(current, body, InforOpportunityDto.class))
                .defaultIfEmpty(withOpportunityId(convert(body, InforOpportunityDto.class), id))
                .flatMap(value -> inforClient.upsertOpportunity(value)
                        .then(inforClient.getOpportunity(id))
                        .map(saved -> sdataResource(id, "opportunities", saved)));
    }

    @GetMapping("/infor/orders")
    public Flux<Map<String, Object>> listOrders() {
        return inforClient.listOrders().map(value -> sdataResource(value.orderNumber(), "orders", value));
    }

    @GetMapping("/infor/orders/{id}")
    public Mono<InforOrderDto> getOrder(@PathVariable String id) {
        return required(inforClient.getOrder(id), "INFOR order", id);
    }

    @PostMapping("/infor/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) {
        InforOrderDto value = convert(body, InforOrderDto.class);
        String id = "ORD-MOCK-" + UUID.randomUUID();
        InforOrderDto created = withOrderId(value, id);
        return inforClient.upsertOrder(created).thenReturn(sdataResource(id, "orders", created));
    }

    @PutMapping("/infor/orders/{id}")
    public Mono<Map<String, Object>> putOrder(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return inforClient.getOrder(id)
                .map(current -> merge(current, body, InforOrderDto.class))
                .defaultIfEmpty(withOrderId(convert(body, InforOrderDto.class), id))
                .flatMap(value -> inforClient.upsertOrder(value)
                        .then(inforClient.getOrder(id))
                        .map(saved -> sdataResource(id, "orders", saved)));
    }

    @GetMapping("/infor/invoices")
    public Flux<Map<String, Object>> listInvoices() {
        return inforClient.listInvoices().map(value -> sdataResource(value.invoiceNumber(), "invoices", value));
    }

    @GetMapping("/infor/invoices/{id}")
    public Mono<InforInvoiceDto> getInvoice(@PathVariable String id) {
        return required(inforClient.getInvoice(id), "INFOR invoice", id);
    }

    @PostMapping("/infor/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createInvoice(@RequestBody Map<String, Object> body) {
        InforInvoiceDto value = convert(body, InforInvoiceDto.class);
        String id = "INV-MOCK-" + UUID.randomUUID();
        InforInvoiceDto created = new InforInvoiceDto(id, value.orderNumber(), value.accountId(),
                value.customerName(), value.invoiceDate(), value.amount(), value.lineItems());
        return inforClient.upsertInvoice(created).thenReturn(sdataResource(id, "invoices", created));
    }

    @PutMapping("/infor/invoices/{id}")
    public Mono<Map<String, Object>> putInvoice(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return inforClient.getInvoice(id)
                .map(current -> merge(current, body, InforInvoiceDto.class))
                .defaultIfEmpty(withInvoiceId(convert(body, InforInvoiceDto.class), id))
                .flatMap(value -> inforClient.upsertInvoice(value)
                        .then(inforClient.getInvoice(id))
                        .map(saved -> sdataResource(id, "invoices", saved)));
    }

    @GetMapping("/infor/products")
    public Flux<Map<String, Object>> listProducts() {
        return inforClient.listProducts().map(value -> sdataResource(value.productSku(), "products", value));
    }

    @GetMapping("/infor/products/{id}")
    public Mono<InforProductDto> getProduct(@PathVariable String id) {
        return required(inforClient.getProduct(id), "INFOR product", id);
    }

    @PostMapping("/infor/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createProduct(@RequestBody Map<String, Object> body) {
        InforProductDto value = convert(body, InforProductDto.class);
        String id = "SKU-MOCK-" + UUID.randomUUID();
        InforProductDto created = new InforProductDto(id, value.name(), value.unitPrice(), value.unitCost(),
                value.profit(), value.productCategory(), value.hubSpotLineItemId());
        return inforClient.upsertProduct(created).thenReturn(sdataResource(id, "products", created));
    }

    @PutMapping("/infor/products/{id}")
    public Mono<Map<String, Object>> putProduct(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return inforClient.getProduct(id)
                .map(current -> merge(current, body, InforProductDto.class))
                .defaultIfEmpty(withProductSku(convert(body, InforProductDto.class), id))
                .flatMap(value -> inforClient.upsertProduct(value)
                        .then(inforClient.getProduct(id))
                        .map(saved -> sdataResource(id, "products", saved)));
    }

    @GetMapping("/hubspot/{objectType}/{id}")
    public Mono<Map<String, Object>> getHubSpotObject(@PathVariable String objectType, @PathVariable String id) {
        String normalizedType = normalizeHubSpotObjectType(objectType);
        return hubSpotClient.get(normalizedType, id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "HubSpot " + normalizedType + " not found: " + id)))
                .map(properties -> {
                    Map<String, Object> response = new LinkedHashMap<>(properties);
                    response.put("id", id);
                    return response;
                });
    }

    @PostMapping("/hubspot/deals/{id}")
    public Mono<Void> updateHubSpotDeal(@PathVariable String id, @RequestBody Map<String, Object> properties) {
        if (properties.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide at least one HubSpot deal property to update"));
        }
        return hubSpotClient.get("deals", id)
                .switchIfEmpty(Mono.defer(() -> {
                    Object opportunityId = properties.get("infor_opportunity_id");
                    if (opportunityId == null || opportunityId.toString().isBlank()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "infor_opportunity_id is required when creating a HubSpot deal"));
                    }
                    return Mono.just(Map.<String, Object>of());
                }))
                .flatMap(existing -> {
                    Map<String, Object> merged = new LinkedHashMap<>(existing);
                    merged.putAll(properties);
                    return hubSpotClient.update("deals", id, new HubSpotObjectRequest(merged));
                });
    }

    @PostMapping("/sync/opportunities/{id}")
    public Mono<Void> syncOpportunity(@PathVariable String id) {
        return dealWorkflow.syncInforOpportunityToHubSpot(id);
    }

    @PostMapping({"/sync/hubspot/deals/{id}", "/hubspot/deals/{id}/sync-to-infor"})
    public Mono<Void> syncDealToInfor(@PathVariable String id) {
        return dealWorkflow.syncHubSpotDealToInfor(id);
    }

    @PostMapping("/sync/orders/{id}")
    public Mono<BusinessSyncResult> syncOrder(@PathVariable String id) {
        return orderWorkflow.syncOrderToHubSpot(id);
    }

    @PostMapping("/sync/invoices/{id}")
    public Mono<BusinessSyncResult> syncInvoice(@PathVariable String id) {
        return invoiceWorkflow.syncInvoiceToHubSpot(id);
    }

    @PostMapping("/sync/products/{id}")
    public Mono<BusinessSyncResult> syncProduct(@PathVariable String id) {
        return productWorkflow.syncProductToHubSpotLineItem(id);
    }

    private <T> Mono<T> required(Mono<T> result, String kind, String id) {
        return result.switchIfEmpty(Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND, kind + " not found: " + id)));
    }

    private <T> T convert(Map<String, Object> body, Class<T> type) {
        Map<String, Object> values = new LinkedHashMap<>(body);
        values.keySet().removeIf(key -> key.startsWith("$"));
        try {
            return objectMapper.convertValue(values, type);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid INFOR mock resource for " + type.getSimpleName(), exception);
        }
    }

    private <T> T merge(T current, Map<String, Object> updates, Class<T> type) {
        Map<String, Object> values = objectMapper.convertValue(current, new TypeReference<>() {});
        updates.forEach((key, value) -> {
            if (!key.startsWith("$")) {
                values.put(key, value);
            }
        });
        return convert(values, type);
    }

    private InforOpportunityDto withOpportunityId(InforOpportunityDto value, String id) {
        return new InforOpportunityDto(id, value.hubSpotDealId(), value.opportunityName(), value.accountId(),
                value.accountManager(), value.estimatedCloseDate(), value.actualCloseDate(), value.potentialAmount(),
                value.actualAmount(), value.probability(), value.stage(), value.status(), value.reasonWon(),
                value.leadSource(), value.owner(), value.opportunityType(), value.description(), value.contactIds(),
                value.productSkus(), value.salesOrderIds());
    }

    private InforOrderDto withOrderId(InforOrderDto value, String id) {
        return new InforOrderDto(id, value.hubSpotOrderId(), value.enteredDate(), value.transactionType(),
                value.stage(), value.customerName(), value.customerNumber(), value.accountId(),
                value.quantityOrdered(), value.reference(),
                value.takenBy(), value.shipToName(), value.division(), value.shippedDate(), value.invoicedDate(),
                value.invoicedAmount(), value.orderedAmount(), value.shippedAmount(), value.insideSalesRep(),
                value.insideSalesRepName(), value.outsideSalesRep(), value.outsideSalesRepName(), value.takenByName(),
                value.vendorReturnAuthorization(), value.orderNotes(), value.customerNotes(), value.lineItems());
    }

    private InforInvoiceDto withInvoiceId(InforInvoiceDto value, String id) {
        return new InforInvoiceDto(id, value.orderNumber(), value.accountId(), value.customerName(),
                value.invoiceDate(), value.amount(), value.lineItems());
    }

    private InforProductDto withProductSku(InforProductDto value, String id) {
        return new InforProductDto(id, value.name(), value.unitPrice(), value.unitCost(), value.profit(),
                value.productCategory(), value.hubSpotLineItemId());
    }

    private Map<String, Object> sdataResource(String id, String collection, Object value) {
        Map<String, Object> resource = objectMapper.convertValue(value, new TypeReference<>() {});
        resource.put("$key", id);
        resource.put("$url", collection + "('" + id + "')");
        resource.put("$updated", java.time.Instant.now().toString());
        return resource;
    }

    private String normalizeHubSpotObjectType(String objectType) {
        return switch (objectType) {
            case "deal", "deals" -> "deals";
            case "order", "orders" -> "orders";
            case "line-item", "line-items", "line_item", "line_items" -> "line_items";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported HubSpot business object type: " + objectType);
        };
    }
}
