package com.modernice.integration.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modernice.integration.connector.infor.business.InforBusinessClient;
import com.modernice.integration.connector.infor.business.dto.InforInvoiceDto;
import com.modernice.integration.connector.infor.business.dto.InforOpportunityDto;
import com.modernice.integration.connector.infor.business.dto.InforOrderDto;
import com.modernice.integration.connector.infor.business.dto.InforProductDto;
import com.modernice.integration.connector.infor.crm.InforCrmClient;
import com.modernice.integration.connector.infor.crm.InforSDataMapper;
import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

@RestController
@Profile("local")
@RequestMapping("/mock/infor-sdata/sdata/slx/dynamic/-")
public class MockInforBusinessSDataController {
    private static final String MOCK_SDATA_ROOT = "/mock/infor-sdata/sdata/slx/dynamic/-/";
    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";

    private final InforBusinessClient client;
    private final InforCrmClient crmClient;
    private final InforSDataMapper crmMapper;
    private final ObjectMapper objectMapper;

    public MockInforBusinessSDataController(
            InforBusinessClient client,
            InforCrmClient crmClient,
            InforSDataMapper crmMapper,
            ObjectMapper objectMapper) {
        this.client = client;
        this.crmClient = crmClient;
        this.crmMapper = crmMapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/opportunities")
    public Mono<Map<String, Object>> listOpportunities(
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count,
            ServerHttpRequest request) {
        return client.listOpportunities()
                .map(value -> resource(value.opportunityId(), "opportunities", value, request))
                .collectList()
                .map(resources -> feed(request, resources, startIndex, count));
    }

    @PostMapping("/opportunities")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createOpportunity(
            @RequestBody Map<String, Object> body, ServerHttpRequest request) {
        InforOpportunityDto value = convert(body, InforOpportunityDto.class);
        String id = "OPP-MOCK-" + UUID.randomUUID();
        InforOpportunityDto created = new InforOpportunityDto(id, value.hubSpotDealId(), value.opportunityName(),
                value.accountId(), value.accountManager(), value.estimatedCloseDate(), value.actualCloseDate(),
                value.potentialAmount(), value.actualAmount(), value.probability(), value.stage(), value.status(),
                value.reasonWon(), value.leadSource(), value.owner(), value.opportunityType(), value.description(),
                value.contactIds(), value.productSkus(), value.salesOrderIds());
        return validateOpportunityAssociations(created)
                .then(client.upsertOpportunity(created))
                .thenReturn(resource(id, "opportunities", created, request));
    }

    @GetMapping({"/opportunities/{id}", "/opportunities('{id}')"})
    public Mono<Map<String, Object>> getOpportunity(@PathVariable String id, ServerHttpRequest request) {
        return required(client.getOpportunity(id), "Opportunity", id)
                .map(value -> resource(id, "opportunities", value, request));
    }

    @PutMapping({"/opportunities/{id}", "/opportunities('{id}')"})
    public Mono<Map<String, Object>> updateOpportunity(
            @PathVariable String id, @RequestBody Map<String, Object> updates, ServerHttpRequest request) {
        return client.getOpportunity(id)
                .map(current -> merge(current, updates, InforOpportunityDto.class))
                .defaultIfEmpty(withOpportunityId(convert(updates, InforOpportunityDto.class), id))
                .flatMap(value -> validateOpportunityAssociations(value)
                        .then(client.upsertOpportunity(value))
                        .then(Mono.defer(() -> client.getOpportunity(id)))
                        .map(saved -> resource(id, "opportunities", saved, request)));
    }

    @GetMapping({"/opportunities/{id}/Contacts", "/opportunities('{id}')/Contacts"})
    public Mono<Map<String, Object>> listOpportunityContacts(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count,
            ServerHttpRequest request) {
        return opportunityChildFeed(id, InforOpportunityDto::contactIds,
                contactId -> crmClient.getContact(contactId), "Contact", "contacts",
                (contactId, contact) -> contactResource(contactId, contact, request),
                startIndex, count, request);
    }

    @GetMapping({"/opportunities/{id}/Products", "/opportunities('{id}')/Products"})
    public Mono<Map<String, Object>> listOpportunityProducts(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count,
            ServerHttpRequest request) {
        return opportunityChildFeed(id, InforOpportunityDto::productSkus,
                client::getProduct, "Product", "products",
                (productId, product) -> resource(productId, "products", product, request),
                startIndex, count, request);
    }

    @GetMapping({"/opportunities/{id}/SalesOrders", "/opportunities('{id}')/SalesOrders"})
    public Mono<Map<String, Object>> listOpportunitySalesOrders(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count,
            ServerHttpRequest request) {
        return opportunityChildFeed(id, InforOpportunityDto::salesOrderIds,
                client::getOrder, "SalesOrder", "orders",
                (orderId, order) -> resource(orderId, "orders", order, request),
                startIndex, count, request);
    }

    @GetMapping("/orders")
    public Mono<Map<String, Object>> listOrders(
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count,
            ServerHttpRequest request) {
        return client.listOrders().map(value -> resource(value.orderNumber(), "orders", value, request))
                .collectList()
                .map(resources -> feed(request, resources, startIndex, count));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createOrder(
            @RequestBody Map<String, Object> body, ServerHttpRequest request) {
        InforOrderDto value = convert(body, InforOrderDto.class);
        String id = "ORD-MOCK-" + UUID.randomUUID();
        InforOrderDto created = withOrderId(value, id);
        return client.upsertOrder(created).thenReturn(resource(id, "orders", created, request));
    }

    @GetMapping({"/orders/{id}", "/orders('{id}')"})
    public Mono<Map<String, Object>> getOrder(@PathVariable String id, ServerHttpRequest request) {
        return required(client.getOrder(id), "Order", id).map(value -> resource(id, "orders", value, request));
    }

    @PutMapping({"/orders/{id}", "/orders('{id}')"})
    public Mono<Map<String, Object>> updateOrder(
            @PathVariable String id, @RequestBody Map<String, Object> updates, ServerHttpRequest request) {
        return client.getOrder(id)
                .map(current -> merge(current, updates, InforOrderDto.class))
                .defaultIfEmpty(withOrderId(convert(updates, InforOrderDto.class), id))
                .flatMap(value -> client.upsertOrder(value)
                        .then(Mono.defer(() -> client.getOrder(id)))
                        .map(saved -> resource(id, "orders", saved, request)));
    }

    @GetMapping("/invoices")
    public Mono<Map<String, Object>> listInvoices(
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count,
            ServerHttpRequest request) {
        return client.listInvoices().map(value -> resource(value.invoiceNumber(), "invoices", value, request))
                .collectList()
                .map(resources -> feed(request, resources, startIndex, count));
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createInvoice(
            @RequestBody Map<String, Object> body, ServerHttpRequest request) {
        InforInvoiceDto value = convert(body, InforInvoiceDto.class);
        String id = "INV-MOCK-" + UUID.randomUUID();
        InforInvoiceDto created = new InforInvoiceDto(id, value.orderNumber(), value.accountId(),
                value.customerName(), value.invoiceDate(), value.amount(), value.lineItems());
        return client.upsertInvoice(created).thenReturn(resource(id, "invoices", created, request));
    }

    @GetMapping({"/invoices/{id}", "/invoices('{id}')"})
    public Mono<Map<String, Object>> getInvoice(@PathVariable String id, ServerHttpRequest request) {
        return required(client.getInvoice(id), "Invoice", id).map(value -> resource(id, "invoices", value, request));
    }

    @PutMapping({"/invoices/{id}", "/invoices('{id}')"})
    public Mono<Map<String, Object>> updateInvoice(
            @PathVariable String id, @RequestBody Map<String, Object> updates, ServerHttpRequest request) {
        return client.getInvoice(id)
                .map(current -> merge(current, updates, InforInvoiceDto.class))
                .defaultIfEmpty(withInvoiceId(convert(updates, InforInvoiceDto.class), id))
                .flatMap(value -> client.upsertInvoice(value)
                        .then(Mono.defer(() -> client.getInvoice(id)))
                        .map(saved -> resource(id, "invoices", saved, request)));
    }

    @GetMapping("/products")
    public Mono<Map<String, Object>> listProducts(
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count,
            ServerHttpRequest request) {
        return client.listProducts().map(value -> resource(value.productSku(), "products", value, request))
                .collectList()
                .map(resources -> feed(request, resources, startIndex, count));
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createProduct(
            @RequestBody Map<String, Object> body, ServerHttpRequest request) {
        InforProductDto value = convert(body, InforProductDto.class);
        String id = "SKU-MOCK-" + UUID.randomUUID();
        InforProductDto created = new InforProductDto(id, value.name(), value.unitPrice(), value.unitCost(),
                value.profit(), value.productCategory(), value.hubSpotLineItemId());
        return client.upsertProduct(created).thenReturn(resource(id, "products", created, request));
    }

    @GetMapping({"/products/{id}", "/products('{id}')"})
    public Mono<Map<String, Object>> getProduct(@PathVariable String id, ServerHttpRequest request) {
        return required(client.getProduct(id), "Product", id).map(value -> resource(id, "products", value, request));
    }

    @PutMapping({"/products/{id}", "/products('{id}')"})
    public Mono<Map<String, Object>> updateProduct(
            @PathVariable String id, @RequestBody Map<String, Object> updates, ServerHttpRequest request) {
        return client.getProduct(id)
                .map(current -> merge(current, updates, InforProductDto.class))
                .defaultIfEmpty(withProductSku(convert(updates, InforProductDto.class), id))
                .flatMap(value -> client.upsertProduct(value)
                        .then(Mono.defer(() -> client.getProduct(id)))
                        .map(saved -> resource(id, "products", saved, request)));
    }

    private <T> Mono<T> required(Mono<T> result, String kind, String id) {
        return result.switchIfEmpty(Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "INFOR " + kind.toLowerCase() + " not found: " + id)));
    }

    private <T> T convert(Map<String, Object> body, Class<T> type) {
        Map<String, Object> values = normalizeReferences(body, type);
        values.keySet().removeIf(key -> key.startsWith("$"));
        try {
            return objectMapper.convertValue(values, type);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid SData mock resource for " + type.getSimpleName(), exception);
        }
    }

    private <T> T merge(T current, Map<String, Object> updates, Class<T> type) {
        JsonNode currentNode = objectMapper.valueToTree(current);
        if (!(currentNode instanceof ObjectNode merged)) {
            throw new IllegalStateException("Expected object-shaped business resource for " + type.getSimpleName());
        }
        normalizeReferences(updates, type).forEach((key, value) -> {
            if (!key.startsWith("$")) {
                JsonNode update = objectMapper.valueToTree(value);
                merged.set(key, update);
            }
        });
        try {
            return objectMapper.treeToValue(merged, type);
        } catch (java.io.IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid SData mock resource for " + type.getSimpleName(), exception);
        }
    }

    private Map<String, Object> resource(
            String id, String collection, Object value, ServerHttpRequest request) {
        Map<String, Object> result = objectMapper.convertValue(value, new TypeReference<>() {});
        result.put("$key", id);
        result.put("$url", resourceUrl(request, collection, id));
        result.put("$lookup", collectionUrl(request, collection) + "?format=json");
        result.put("$updated", Instant.now().toString());

        if (value instanceof InforOpportunityDto opportunity) {
            result.remove("accountId");
            result.remove("contactIds");
            result.remove("productSkus");
            result.remove("salesOrderIds");
            put(result, "Account", reference(request, "accounts", opportunity.accountId()));
            result.put("Contacts", collectionNavigation(request, id, "Contacts"));
            result.put("Products", collectionNavigation(request, id, "Products"));
            result.put("SalesOrders", collectionNavigation(request, id, "SalesOrders"));
        } else if (value instanceof InforOrderDto order) {
            result.remove("accountId");
            put(result, "Account", reference(request, "accounts", order.accountId()));
            result.put("lineItems", lineItemResources(order.lineItems(), request));
        } else if (value instanceof InforInvoiceDto invoice) {
            result.remove("accountId");
            result.remove("orderNumber");
            put(result, "Account", reference(request, "accounts", invoice.accountId()));
            put(result, "SalesOrder", reference(request, "orders", invoice.orderNumber()));
            result.put("lineItems", lineItemResources(invoice.lineItems(), request));
        }
        return result;
    }

    private Map<String, Object> normalizeReferences(Map<String, Object> source, Class<?> type) {
        Map<String, Object> values = new LinkedHashMap<>(source);
        if (type == InforOpportunityDto.class || type == InforOrderDto.class || type == InforInvoiceDto.class) {
            copyReferenceKey(values, "Account", "accountId");
        }
        if (type == InforOpportunityDto.class) {
            copyReferenceKeys(values, "Contacts", "contactIds");
            copyReferenceKeys(values, "Products", "productSkus");
            copyReferenceKeys(values, "SalesOrders", "salesOrderIds");
        }
        if (type == InforInvoiceDto.class) {
            copyReferenceKey(values, "SalesOrder", "orderNumber");
            copyReferenceKey(values, "Order", "orderNumber");
        }
        if (type == InforOrderDto.class || type == InforInvoiceDto.class) {
            normalizeLineItems(values);
        }
        return values;
    }

    private <T> Mono<Map<String, Object>> opportunityChildFeed(
            String opportunityId,
            Function<InforOpportunityDto, List<String>> associationIds,
            Function<String, Mono<T>> lookup,
            String relatedType,
            String collection,
            BiFunction<String, T, Map<String, Object>> resourceMapper,
            int startIndex,
            int count,
            ServerHttpRequest request) {
        return required(client.getOpportunity(opportunityId), "Opportunity", opportunityId)
                .flatMap(opportunity -> Flux.fromIterable(associationIds.apply(opportunity))
                        .concatMap(relatedId -> required(lookup.apply(relatedId), relatedType, relatedId)
                                .map(related -> resourceMapper.apply(relatedId, related)))
                        .collectList()
                        .map(resources -> feed(request, resources, startIndex, count)));
    }

    private Mono<Void> validateOpportunityAssociations(InforOpportunityDto opportunity) {
        return Mono.when(
                validateReferences(opportunity.contactIds(),
                        id -> required(crmClient.getContact(id), "Contact", id)),
                validateReferences(opportunity.productSkus(),
                        id -> required(client.getProduct(id), "Product", id)),
                validateReferences(opportunity.salesOrderIds(),
                        id -> required(client.getOrder(id), "SalesOrder", id)));
    }

    private <T> Mono<Void> validateReferences(List<String> ids, Function<String, Mono<T>> lookup) {
        return Flux.fromIterable(ids).concatMap(lookup).then();
    }

    private Map<String, Object> contactResource(
            String id, InforContactDto contact, ServerHttpRequest request) {
        Map<String, Object> result = new LinkedHashMap<>(crmMapper.toSDataContact(contact));
        result.put("$key", id);
        result.put("$url", resourceUrl(request, "contacts", id));
        result.put("$lookup", collectionUrl(request, "contacts") + "?format=json");
        result.put("$updated", Instant.now().toString());
        put(result, "Account", reference(request, "accounts", contact.crmAccountId()));
        result.put("Address", reference(request, "addresses", "contact-" + id));
        return result;
    }

    private void normalizeLineItems(Map<String, Object> values) {
        Object lineItemsValue = values.get("lineItems");
        if (!(lineItemsValue instanceof List<?> items)) {
            return;
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each lineItems entry must be a JSON object");
            }
            Map<String, Object> lineItem = new LinkedHashMap<>();
            itemMap.forEach((key, value) -> lineItem.put(String.valueOf(key), value));
            copyReferenceKey(lineItem, "Product", "productSku");
            lineItem.keySet().removeIf(key -> key.startsWith("$"));
            normalized.add(lineItem);
        }
        values.put("lineItems", normalized);
    }

    private static void copyReferenceKey(Map<String, Object> values, String referenceName, String targetName) {
        Object reference = values.remove(referenceName);
        if (reference == null || values.containsKey(targetName)) {
            return;
        }
        if (reference instanceof Map<?, ?> referenceMap) {
            Object key = referenceMap.get("$key");
            if (key != null) {
                values.put(targetName, key);
            }
        } else if (reference instanceof String key) {
            values.put(targetName, key);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    referenceName + " must contain an SData $key reference");
        }
    }

    private static void copyReferenceKeys(Map<String, Object> values, String referenceName, String targetName) {
        Object references = values.remove(referenceName);
        if (references == null || values.containsKey(targetName)) {
            return;
        }
        if (!(references instanceof List<?> referenceList)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    referenceName + " must be an array of SData $key references");
        }
        List<String> keys = new ArrayList<>();
        for (Object reference : referenceList) {
            if (reference instanceof Map<?, ?> referenceMap && referenceMap.get("$key") != null) {
                keys.add(String.valueOf(referenceMap.get("$key")));
            } else if (reference instanceof String key && !key.isBlank()) {
                keys.add(key);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each " + referenceName + " entry must contain an SData $key reference");
            }
        }
        values.put(targetName, keys);
    }

    private List<Map<String, Object>> lineItemResources(
            List<com.modernice.integration.connector.infor.business.dto.InforLineItemDto> lineItems,
            ServerHttpRequest request) {
        if (lineItems == null) {
            return List.of();
        }
        return lineItems.stream().map(lineItem -> {
            Map<String, Object> result = objectMapper.convertValue(lineItem, new TypeReference<>() {});
            put(result, "Product", reference(request, "products", lineItem.productSku()));
            return result;
        }).toList();
    }

    private Map<String, Object> reference(ServerHttpRequest request, String collection, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("$key", id);
        reference.put("$url", resourceUrl(request, collection, id));
        reference.put("$uuid", ZERO_UUID);
        reference.put("$lookup", collectionUrl(request, collection) + "?format=json");
        return reference;
    }

    private Map<String, Object> collectionNavigation(ServerHttpRequest request, String id, String navigation) {
        return Map.of("$url", resourceUrl(request, "opportunities", id) + "/" + navigation);
    }

    private String resourceUrl(ServerHttpRequest request, String collection, String id) {
        return collectionUrl(request, collection) + "('" + id.replace("'", "''") + "')";
    }

    private String collectionUrl(ServerHttpRequest request, String collection) {
        URI uri = request.getURI();
        return uri.getScheme() + "://" + uri.getRawAuthority() + MOCK_SDATA_ROOT + collection;
    }

    private Map<String, Object> feed(
            ServerHttpRequest request,
            List<Map<String, Object>> resources,
            int startIndex,
            int count) {
        if (startIndex < 1 || count < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startIndex and count must be positive integers");
        }
        int fromIndex = Math.min(startIndex - 1, resources.size());
        int toIndex = fromIndex + Math.min(count, resources.size() - fromIndex);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("$url", request.getURI().toString());
        result.put("$totalResults", resources.size());
        result.put("$startIndex", startIndex);
        result.put("$itemsPerPage", toIndex - fromIndex);
        result.put("$resources", resources.subList(fromIndex, toIndex));
        return result;
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
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
}
