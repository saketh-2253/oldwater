package com.modernice.integration.connector.infor.crm.dto;

import java.time.Instant;

public record InforContactDto(
        String crmContactId,
        String firstName,
        String lastName,
        String account,
        String miCustomerId,
        String crmAccountId,
        String city,
        String state,
        String workPhone,
        String mobile,
        String email,
        String acctManager,
        String subType,
        String erpTerritory,
        Instant createDate,
        Instant modifyDate,
        String owner,
        String erpContactNo,
        String hubSpotRecordId
) {}
