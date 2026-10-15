package com.modernice.integration.workflow;

import com.modernice.integration.connector.hubspot.SalesHubSpotClient;
import com.modernice.integration.connector.hubspot.dto.HubSpotObjectRequest;
import com.modernice.integration.connector.hubspot.mapper.HubSpotLineItemMapper;
import com.modernice.integration.connector.hubspot.mapper.HubSpotOrderMapper;
import com.modernice.integration.connector.infor.business.InforBusinessClient;
import com.modernice.integration.connector.infor.business.mapper.InforBusinessMapper;
import com.modernice.integration.domain.model.LineItem;
import com.modernice.integration.domain.model.Order;
import com.modernice.integration.identity.BusinessIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderSyncWorkflow {
    private final InforBusinessClient inforClient;
    private final InforBusinessMapper inforMapper;
    private final HubSpotOrderMapper orderMapper;
    private final HubSpotLineItemMapper lineItemMapper;
    private final SalesHubSpotClient hubSpotClient;
    private final BusinessIdentityService identity;

    public Mono<BusinessSyncResult> syncOrderToHubSpot(String orderNumber) {
        return inforClient.getOrder(orderNumber)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "INFOR order not found: " + orderNumber)))
                .map(inforMapper::toCanonical)
                .flatMap(order -> upsertHubSpotOrder(order)
                        .flatMap(hubSpotOrderId -> inforClient.upsertOrder(
                                        inforMapper.toInfor(withHubSpotOrderId(order, hubSpotOrderId)))
                                .thenMany(Flux.fromIterable(order.lineItems())
                                        .concatMap(line -> syncLineItem(line, hubSpotOrderId, "LINE_ITEM")))
                                .collectList()
                                .map(lineItemIds -> new BusinessSyncResult(
                                        orderNumber, "orders", hubSpotOrderId, lineItemIds))));
    }

    Mono<String> upsertHubSpotOrder(Order order) {
        var request = new HubSpotObjectRequest(orderMapper.toHubSpot(order));
        Optional<String> existingId = identity.findTargetId("ORDER", "INFOR_ERP", order.orderNumber())
                .or(() -> Optional.ofNullable(order.hubSpotOrderId()));
        return existingId.map(id -> hubSpotClient.update("orders", id, request).thenReturn(id))
                .orElseGet(() -> hubSpotClient.create("orders", request))
                .doOnNext(id -> identity.save("ORDER", "INFOR_ERP", order.orderNumber(),
                        "HUBSPOT", id, order.orderNumber()));
    }

    Mono<String> syncLineItem(LineItem item, String hubSpotOrderId, String entityType) {
        String sourceId = lineItemSourceId(item);
        var request = new HubSpotObjectRequest(lineItemMapper.toHubSpot(item));
        Optional<String> existingId = identity.findTargetId(entityType, "INFOR_ERP", sourceId);
        return existingId.map(id -> hubSpotClient.update("line_items", id, request).thenReturn(id))
                .orElseGet(() -> hubSpotClient.create("line_items", request))
                .flatMap(lineItemId -> {
                    identity.save(entityType, "INFOR_ERP", sourceId, "HUBSPOT", lineItemId, item.productSku());
                    return hubSpotClient.associateLineItemWithOrder(lineItemId, hubSpotOrderId)
                            .thenReturn(lineItemId);
                });
    }

    static String lineItemSourceId(LineItem item) {
        if (item.orderNumber() == null || item.orderSuffix() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Each order line item must include orderNumber and orderSuffix for identity resolution");
        }
        return item.orderNumber() + ":" + item.orderSuffix();
    }

    private Order withHubSpotOrderId(Order order, String hubSpotOrderId) {
        return new Order(order.orderNumber(), order.enteredDate(), order.transactionType(), order.stage(),
                order.customerName(), order.customerNumber(), order.accountId(), order.quantityOrdered(),
                order.reference(),
                order.takenBy(), order.shipToName(), order.division(), order.shippedDate(), order.invoicedDate(),
                order.invoicedAmount(), order.orderedAmount(), order.shippedAmount(), order.insideSalesRep(),
                order.insideSalesRepName(), order.outsideSalesRep(), order.outsideSalesRepName(), order.takenByName(),
                order.vendorReturnAuthorization(), order.orderNotes(), order.customerNotes(), hubSpotOrderId,
                order.lineItems());
    }
}
