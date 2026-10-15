package com.modernice.integration.connector.infor.business;

import com.modernice.integration.connector.infor.business.dto.InforInvoiceDto;
import com.modernice.integration.connector.infor.business.dto.InforOpportunityDto;
import com.modernice.integration.connector.infor.business.dto.InforOrderDto;
import com.modernice.integration.connector.infor.business.dto.InforProductDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InforBusinessClient {
    Flux<InforOpportunityDto> listOpportunities();
    Mono<InforOpportunityDto> getOpportunity(String opportunityId);
    Mono<Void> upsertOpportunity(InforOpportunityDto opportunity);
    Flux<InforOrderDto> listOrders();
    Mono<InforOrderDto> getOrder(String orderNumber);
    Mono<Void> upsertOrder(InforOrderDto order);
    Flux<InforInvoiceDto> listInvoices();
    Mono<InforInvoiceDto> getInvoice(String invoiceNumber);
    Mono<Void> upsertInvoice(InforInvoiceDto invoice);
    Flux<InforProductDto> listProducts();
    Mono<InforProductDto> getProduct(String productSku);
    Mono<Void> upsertProduct(InforProductDto product);
}
