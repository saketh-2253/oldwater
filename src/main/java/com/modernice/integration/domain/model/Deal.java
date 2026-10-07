package com.modernice.integration.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        String description
) {}
