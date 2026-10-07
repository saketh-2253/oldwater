package com.modernice.integration.domain.model;

import com.modernice.integration.domain.enums.EntityType;
import com.modernice.integration.domain.enums.SourceSystem;
import com.modernice.integration.domain.enums.SyncOperation;

import java.time.Instant;
import java.util.UUID;

public record SyncContext(
        UUID eventId,
        SourceSystem sourceSystem,
        SourceSystem targetSystem,
        EntityType entityType,
        SyncOperation operation,
        Instant receivedAt
) {}
