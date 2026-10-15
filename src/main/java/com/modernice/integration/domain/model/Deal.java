package com.modernice.integration.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Deal(
        String opportunityId,
        String hubSpotDealId,
        String opportunityName,
        String accountId,
        String accountManager,
        LocalDate estimatedCloseDate,
        LocalDate actualCloseDate,
        BigDecimal potentialAmount,
        BigDecimal actualAmount,
        BigDecimal probability,
        String stage,
        String status,
        String reasonWon,
        String leadSource,
        String owner,
        String opportunityType,
        String description,
        List<String> contactIds,
        List<String> productSkus,
        List<String> salesOrderIds
) {
    public Deal {
        contactIds = contactIds == null ? List.of() : List.copyOf(contactIds);
        productSkus = productSkus == null ? List.of() : List.copyOf(productSkus);
        salesOrderIds = salesOrderIds == null ? List.of() : List.copyOf(salesOrderIds);
    }

    public Deal(
            String opportunityId,
            String hubSpotDealId,
            String opportunityName,
            String accountId,
            String accountManager,
            LocalDate estimatedCloseDate,
            LocalDate actualCloseDate,
            BigDecimal potentialAmount,
            BigDecimal actualAmount,
            BigDecimal probability,
            String stage,
            String status,
            String reasonWon,
            String leadSource,
            String owner,
            String opportunityType,
            String description) {
        this(opportunityId, hubSpotDealId, opportunityName, accountId, accountManager, estimatedCloseDate,
                actualCloseDate, potentialAmount, actualAmount, probability, stage, status, reasonWon, leadSource,
                owner, opportunityType, description, List.of(), List.of(), List.of());
    }
}
