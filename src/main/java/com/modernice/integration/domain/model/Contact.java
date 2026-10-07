package com.modernice.integration.domain.model;

import java.time.Instant;

public record Contact(
        String crmContactId,
        String firstName,
        String lastName,
        String accountName,
        String miCustomerId,
        String crmAccountId,
        String city,
        String state,
        String workPhone,
        String mobilePhone,
        String email,
        String accountManager,
        String subType,
        String erpTerritory,
        Instant createdDate,
        Instant modifiedDate,
        String owner,
        String erpContactNumber,
        String hubSpotRecordId
) {}
