package com.modernice.integration.connector.hubspot.dto;

import java.util.Map;

public record HubSpotContactRequest(
        Map<String, Object> properties
) {}
