package com.modernice.integration.domain.model;

import java.math.BigDecimal;

public record LineItem(
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
