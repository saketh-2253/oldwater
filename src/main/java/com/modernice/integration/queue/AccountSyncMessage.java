package com.modernice.integration.queue;

import java.util.UUID;

public record AccountSyncMessage(UUID batchId, UUID itemId, String accountId) {}
