package com.modernice.integration.queue;

import java.util.UUID;

public record ContactSyncMessage(UUID batchId, UUID itemId, String crmContactId) {}
