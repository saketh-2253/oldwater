package com.modernice.integration.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AccountSyncBatchRequest(
        @NotEmpty
        @Size(max = 10000)
        List<@NotBlank @Size(max = 255) String> accountIds
) {}
