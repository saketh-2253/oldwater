package com.modernice.integration.connector.infor.business.mapper;

import com.modernice.integration.connector.infor.business.dto.InforInvoiceDto;
import com.modernice.integration.connector.infor.business.dto.InforLineItemDto;
import com.modernice.integration.connector.infor.business.dto.InforOpportunityDto;
import com.modernice.integration.connector.infor.business.dto.InforOrderDto;
import com.modernice.integration.connector.infor.business.dto.InforProductDto;
import com.modernice.integration.domain.model.Deal;
import com.modernice.integration.domain.model.Invoice;
import com.modernice.integration.domain.model.LineItem;
import com.modernice.integration.domain.model.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InforBusinessMapper {

    public Deal toCanonical(InforOpportunityDto dto) {
        return new Deal(dto.opportunityId(), dto.hubSpotDealId(), dto.opportunityName(), dto.accountId(),
                dto.accountManager(), dto.estimatedCloseDate(), dto.actualCloseDate(), dto.potentialAmount(),
                dto.actualAmount(), dto.probability(), dto.stage(), dto.status(), dto.reasonWon(), dto.leadSource(),
                dto.owner(), dto.opportunityType(), dto.description(), dto.contactIds(), dto.productSkus(),
                dto.salesOrderIds());
    }

    public InforOpportunityDto toInfor(Deal deal) {
        return new InforOpportunityDto(deal.opportunityId(), deal.hubSpotDealId(), deal.opportunityName(),
                deal.accountId(), deal.accountManager(), deal.estimatedCloseDate(), deal.actualCloseDate(),
                deal.potentialAmount(), deal.actualAmount(), deal.probability(), deal.stage(), deal.status(),
                deal.reasonWon(), deal.leadSource(), deal.owner(), deal.opportunityType(), deal.description(),
                deal.contactIds(), deal.productSkus(), deal.salesOrderIds());
    }

    public Order toCanonical(InforOrderDto dto) {
        return new Order(dto.orderNumber(), dto.enteredDate(), dto.transactionType(), dto.stage(), dto.customerName(),
                dto.customerNumber(), dto.accountId(), dto.quantityOrdered(), dto.reference(), dto.takenBy(), dto.shipToName(),
                dto.division(), dto.shippedDate(), dto.invoicedDate(), dto.invoicedAmount(), dto.orderedAmount(),
                dto.shippedAmount(), dto.insideSalesRep(), dto.insideSalesRepName(), dto.outsideSalesRep(),
                dto.outsideSalesRepName(), dto.takenByName(), dto.vendorReturnAuthorization(), dto.orderNotes(),
                dto.customerNotes(), dto.hubSpotOrderId(), lineItems(dto.lineItems()));
    }

    public InforOrderDto toInfor(Order order) {
        return new InforOrderDto(order.orderNumber(), order.hubSpotOrderId(), order.enteredDate(),
                order.transactionType(), order.stage(), order.customerName(), order.customerNumber(),
                order.accountId(), order.quantityOrdered(), order.reference(), order.takenBy(), order.shipToName(), order.division(),
                order.shippedDate(), order.invoicedDate(), order.invoicedAmount(), order.orderedAmount(),
                order.shippedAmount(), order.insideSalesRep(), order.insideSalesRepName(), order.outsideSalesRep(),
                order.outsideSalesRepName(), order.takenByName(), order.vendorReturnAuthorization(),
                order.orderNotes(), order.customerNotes(), lineItemDtos(order.lineItems()));
    }

    public Invoice toCanonical(InforInvoiceDto dto) {
        return new Invoice(dto.invoiceNumber(), dto.orderNumber(), dto.accountId(), dto.customerName(),
                dto.invoiceDate(), dto.amount(), lineItems(dto.lineItems()));
    }

    public InforInvoiceDto toInfor(Invoice invoice) {
        return new InforInvoiceDto(invoice.invoiceNumber(), invoice.orderNumber(), invoice.accountId(),
                invoice.customerName(), invoice.invoiceDate(), invoice.amount(), lineItemDtos(invoice.lineItems()));
    }

    public LineItem toCanonical(InforProductDto dto) {
        return new LineItem(null, null, dto.productSku(), dto.name(), dto.unitPrice(), dto.unitCost(), dto.profit(),
                dto.productCategory(), null);
    }

    public InforProductDto toInforProduct(LineItem lineItem) {
        return new InforProductDto(lineItem.productSku(), lineItem.name(), lineItem.unitPrice(), lineItem.unitCost(),
                lineItem.profit(), lineItem.productCategory(), null);
    }

    public LineItem toCanonical(InforLineItemDto dto) {
        return new LineItem(dto.orderNumber(), dto.orderSuffix(), dto.productSku(), dto.name(), dto.unitPrice(),
                dto.unitCost(), dto.profit(), dto.productCategory(), dto.quantity());
    }

    public InforLineItemDto toInfor(LineItem lineItem) {
        return new InforLineItemDto(lineItem.orderNumber(), lineItem.orderSuffix(), lineItem.productSku(),
                lineItem.name(), lineItem.unitPrice(), lineItem.unitCost(), lineItem.profit(),
                lineItem.productCategory(), lineItem.quantity());
    }

    private List<LineItem> lineItems(List<InforLineItemDto> values) {
        return values == null ? List.of() : values.stream().map(this::toCanonical).toList();
    }

    private List<InforLineItemDto> lineItemDtos(List<LineItem> values) {
        return values == null ? List.of() : values.stream().map(this::toInfor).toList();
    }
}
