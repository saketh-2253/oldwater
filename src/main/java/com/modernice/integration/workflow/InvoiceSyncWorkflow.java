package com.modernice.integration.workflow;

import com.modernice.integration.connector.hubspot.SalesHubSpotClient;
import com.modernice.integration.connector.hubspot.dto.HubSpotObjectRequest;
import com.modernice.integration.connector.hubspot.mapper.HubSpotInvoiceMapper;
import com.modernice.integration.connector.infor.business.InforBusinessClient;
import com.modernice.integration.connector.infor.business.mapper.InforBusinessMapper;
import com.modernice.integration.domain.model.Invoice;
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
public class InvoiceSyncWorkflow {
    private final InforBusinessClient inforClient;
    private final InforBusinessMapper inforMapper;
    private final HubSpotInvoiceMapper invoiceMapper;
    private final OrderSyncWorkflow orderSyncWorkflow;
    private final SalesHubSpotClient hubSpotClient;
    private final BusinessIdentityService identity;

    public Mono<BusinessSyncResult> syncInvoiceToHubSpot(String invoiceNumber) {
        return inforClient.getInvoice(invoiceNumber)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "INFOR invoice not found: " + invoiceNumber)))
                .map(inforMapper::toCanonical)
                .flatMap(invoice -> {
                    var request = new HubSpotObjectRequest(invoiceMapper.toHubSpotOrderProperties(invoice));
                    Optional<String> existingOrderId = Optional.ofNullable(invoice.orderNumber())
                            .flatMap(orderNumber -> identity.findTargetId("ORDER", "INFOR_ERP", orderNumber));
                    Optional<String> existingInvoiceId = identity.findTargetId(
                            "INVOICE", "INFOR_ERP", invoice.invoiceNumber());
                    Mono<String> existingOrderFromSource = invoice.orderNumber() == null
                            ? Mono.empty()
                            : inforClient.getOrder(invoice.orderNumber())
                                    .flatMap(order -> Mono.justOrEmpty(order.hubSpotOrderId()));
                    Mono<String> targetId = Mono.justOrEmpty(existingOrderId.or(() -> existingInvoiceId))
                            .switchIfEmpty(existingOrderFromSource);

                    Mono<String> upsert = targetId
                            .flatMap(id -> hubSpotClient.update("orders", id, request).thenReturn(id))
                            .switchIfEmpty(Mono.defer(() -> hubSpotClient.create("orders", request)));
                    return upsert.flatMap(hubSpotOrderId -> {
                        if (invoice.orderNumber() != null && !invoice.orderNumber().isBlank()) {
                            identity.save("ORDER", "INFOR_ERP", invoice.orderNumber(),
                                    "HUBSPOT", hubSpotOrderId, invoice.orderNumber());
                        }
                        identity.save("INVOICE", "INFOR_ERP", invoice.invoiceNumber(),
                                "HUBSPOT", hubSpotOrderId, invoice.orderNumber());
                        return Flux.fromIterable(invoice.lineItems())
                                .concatMap(line -> orderSyncWorkflow.syncLineItem(line, hubSpotOrderId, "INVOICE_LINE_ITEM"))
                                .collectList()
                                .map(lineItemIds -> new BusinessSyncResult(
                                        invoiceNumber, "orders", hubSpotOrderId, lineItemIds));
                    });
                });
    }
}
