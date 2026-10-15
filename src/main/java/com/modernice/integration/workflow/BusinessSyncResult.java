package com.modernice.integration.workflow;

import java.util.List;

public record BusinessSyncResult(
        String sourceId,
        String hubSpotObjectType,
        String hubSpotObjectId,
        List<String> lineItemIds
) {
    public BusinessSyncResult {
        lineItemIds = List.copyOf(lineItemIds);
    }
}
