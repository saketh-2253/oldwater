package com.modernice.integration.connector.infor.business.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InforOrderDto(
        String orderNumber,
        String hubSpotOrderId,
        LocalDate enteredDate,
        String transactionType,
        String stage,
        String customerName,
        String customerNumber,
        String accountId,
        BigDecimal quantityOrdered,
        String reference,
        String takenBy,
        String shipToName,
        String division,
        LocalDate shippedDate,
        LocalDate invoicedDate,
        BigDecimal invoicedAmount,
        BigDecimal orderedAmount,
        BigDecimal shippedAmount,
        String insideSalesRep,
        String insideSalesRepName,
        String outsideSalesRep,
        String outsideSalesRepName,
        String takenByName,
        String vendorReturnAuthorization,
        String orderNotes,
        String customerNotes,
        List<InforLineItemDto> lineItems
) {}
