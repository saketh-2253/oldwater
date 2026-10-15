package com.modernice.integration.connector.infor.business.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InforInvoiceDto(
        String invoiceNumber,
        String orderNumber,
        String accountId,
        String customerName,
        LocalDate invoiceDate,
        BigDecimal amount,
        List<InforLineItemDto> lineItems
) {}
