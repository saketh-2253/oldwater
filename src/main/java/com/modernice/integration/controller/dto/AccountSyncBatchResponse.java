package com.modernice.integration.controller.dto;

import java.util.UUID;

public record AccountSyncBatchResponse(UUID batchId, String statusUrl, int totalAccounts) {}
