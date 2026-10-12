package com.modernice.integration.controller.dto;

import com.modernice.integration.domain.enums.ContactSyncItemStatus;

import java.time.Instant;
import java.util.UUID;

public record AccountSyncBatchItemResponse(
        UUID itemId,
        String accountId,
        ContactSyncItemStatus status,
        String lastError,
        Instant updatedAt
) {}
