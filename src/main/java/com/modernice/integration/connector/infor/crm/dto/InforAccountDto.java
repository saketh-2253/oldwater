package com.modernice.integration.connector.infor.crm.dto;

public record InforAccountDto(
        String customerId,
        String accountId,
        String name,
        String address1,
        String address2,
        String city,
        String state,
        String postalCode,
        String mainPhone,
        String type,
        String subType,
        String status,
        String erpTerritory,
        String accountManager,
        String customerType,
        String owner,
        String hubSpotCompanyId
) {}
