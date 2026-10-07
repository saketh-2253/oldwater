package com.modernice.integration.controller.dto;

import java.util.UUID;

public record ContactSyncBatchResponse(UUID batchId, String statusUrl, int totalContacts) {}
