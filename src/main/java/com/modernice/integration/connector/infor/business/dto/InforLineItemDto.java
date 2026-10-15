package com.modernice.integration.connector.infor.business.dto;

import java.math.BigDecimal;

public record InforLineItemDto(
        String orderNumber,
        String orderSuffix,
        String productSku,
        String name,
        BigDecimal unitPrice,
        BigDecimal unitCost,
        BigDecimal profit,
        String productCategory,
        BigDecimal quantity
) {}
