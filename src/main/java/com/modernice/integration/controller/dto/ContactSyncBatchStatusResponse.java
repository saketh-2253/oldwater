package com.modernice.integration.controller.dto;

import java.time.Instant;
import java.util.UUID;

public record ContactSyncBatchStatusResponse(
        UUID batchId,
        int totalContacts,
        long queued,
        long processing,
        long succeeded,
        long failed,
        Instant createdAt
) {}
