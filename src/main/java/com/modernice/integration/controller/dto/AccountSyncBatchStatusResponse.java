package com.modernice.integration.controller.dto;

import java.time.Instant;
import java.util.UUID;

public record AccountSyncBatchStatusResponse(
        UUID batchId,
        int totalAccounts,
        long queued,
        long processing,
        long succeeded,
        long failed,
        Instant createdAt
) {}
