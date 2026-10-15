package com.modernice.integration.connector.infor.business.dto;

import java.math.BigDecimal;

public record InforProductDto(
        String productSku,
        String name,
        BigDecimal unitPrice,
        BigDecimal unitCost,
        BigDecimal profit,
        String productCategory,
        String hubSpotLineItemId
) {}
