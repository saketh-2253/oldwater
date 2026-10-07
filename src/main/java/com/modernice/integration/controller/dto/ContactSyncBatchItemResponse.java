package com.modernice.integration.controller.dto;

import com.modernice.integration.domain.enums.ContactSyncItemStatus;

import java.time.Instant;
import java.util.UUID;

public record ContactSyncBatchItemResponse(
        UUID itemId,
        String contactId,
        ContactSyncItemStatus status,
        String lastError,
        Instant updatedAt
) {}
