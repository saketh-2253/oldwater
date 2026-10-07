package com.modernice.integration.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Order(
        String orderNumber,
        LocalDate enteredDate,
        String transactionType,
        String stage,
        String customerName,
        String customerNumber,
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
        String hubSpotOrderId,
        List<LineItem> lineItems
) {}
