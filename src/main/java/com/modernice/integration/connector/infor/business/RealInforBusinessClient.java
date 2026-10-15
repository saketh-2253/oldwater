package com.modernice.integration.connector.infor.business;

import com.modernice.integration.connector.infor.business.dto.InforInvoiceDto;
import com.modernice.integration.connector.infor.business.dto.InforOpportunityDto;
import com.modernice.integration.connector.infor.business.dto.InforOrderDto;
import com.modernice.integration.connector.infor.business.dto.InforProductDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Profile("!local")
public class RealInforBusinessClient implements InforBusinessClient {
    private final InforBusinessProperties properties;

    public RealInforBusinessClient(InforBusinessProperties properties) {
        this.properties = properties;
    }

    public InforBusinessProperties getConfiguration() {
        return properties;
    }

    @Override
    public Flux<InforOpportunityDto> listOpportunities() {
        return notConfiguredFlux("opportunity list");
    }

    @Override
    public Mono<InforOpportunityDto> getOpportunity(String opportunityId) {
        return notConfigured("opportunity read");
    }

    @Override
    public Mono<Void> upsertOpportunity(InforOpportunityDto opportunity) {
        return notConfigured("opportunity upsert");
    }

    @Override
    public Flux<InforOrderDto> listOrders() {
        return notConfiguredFlux("order list");
    }

    @Override
    public Mono<InforOrderDto> getOrder(String orderNumber) {
        return notConfigured("order read");
    }

    @Override
    public Mono<Void> upsertOrder(InforOrderDto order) {
        return notConfigured("order upsert");
    }

    @Override
    public Flux<InforInvoiceDto> listInvoices() {
        return notConfiguredFlux("invoice list");
    }

    @Override
    public Mono<InforInvoiceDto> getInvoice(String invoiceNumber) {
        return notConfigured("invoice read");
    }

    @Override
    public Mono<Void> upsertInvoice(InforInvoiceDto invoice) {
        return notConfigured("invoice upsert");
    }

    @Override
    public Flux<InforProductDto> listProducts() {
        return notConfiguredFlux("product list");
    }

    @Override
    public Mono<InforProductDto> getProduct(String productSku) {
        return notConfigured("product read");
    }

    @Override
    public Mono<Void> upsertProduct(InforProductDto product) {
        return notConfigured("product upsert");
    }

    private <T> Mono<T> notConfigured(String operation) {
        return Mono.error(new IllegalStateException(
                "Real INFOR " + operation + " is not implemented: confirm the CRM/ERP/Data Lake endpoint, "
                        + "authentication, and payload contract before enabling this operation. "
                        + "Configured CRM, ERP, and Data Lake URLs are available through InforBusinessProperties."));
    }

    private <T> Flux<T> notConfiguredFlux(String operation) {
        return Flux.error(new IllegalStateException(
                "Real INFOR " + operation + " is not implemented: confirm the CRM/ERP/Data Lake endpoint, "
                        + "authentication, and payload contract before enabling this operation."));
    }
}
