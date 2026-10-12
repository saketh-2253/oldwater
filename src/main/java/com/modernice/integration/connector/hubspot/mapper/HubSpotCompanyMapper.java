package com.modernice.integration.connector.hubspot.mapper;

import com.modernice.integration.connector.hubspot.dto.HubSpotCompanyRequest;
import com.modernice.integration.domain.model.Company;
import com.modernice.integration.mapping.ValueMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HubSpotCompanyMapper {

    private final ValueMappingService valueMappingService;

    public HubSpotCompanyRequest toHubSpot(Company company) {
        Map<String, Object> properties = new LinkedHashMap<>();
        put(properties, "customer_id", company.customerId());
        put(properties, "name", company.name());
        put(properties, "address", company.address1());
        put(properties, "address2", company.address2());
        put(properties, "city", company.city());
        put(properties, "state", company.state());
        put(properties, "zip", company.postalCode());
        put(properties, "phone", company.mainPhone());
        put(properties, "subtype", company.subType());
        put(properties, "status", company.status());
        put(properties, "erp_territory", valueMappingService.map("ERP_TERRITORY", company.erpTerritory()));
        put(properties, "account_manager", company.accountManager());
        put(properties, "customer_type", valueMappingService.map("CUSTOMER_TYPE", company.customerType()));
        put(properties, "infor_owner", company.owner());
        put(properties, "infor_account_id", company.accountId());
        return new HubSpotCompanyRequest(properties);
    }

    public Company fromHubSpot(Map<String, Object> properties, String hubSpotCompanyId) {
        return new Company(
                asString(properties.get("customer_id")),
                asString(properties.get("infor_account_id")),
                asString(properties.get("name")),
                asString(properties.get("address")),
                asString(properties.get("address2")),
                asString(properties.get("city")),
                asString(properties.get("state")),
                asString(properties.get("zip")),
                asString(properties.get("phone")),
                null,
                asString(properties.get("subtype")),
                asString(properties.get("status")),
                valueMappingService.unmap("ERP_TERRITORY", asString(properties.get("erp_territory"))),
                asString(properties.get("account_manager")),
                valueMappingService.unmap("CUSTOMER_TYPE", asString(properties.get("customer_type"))),
                asString(properties.get("infor_owner")),
                hubSpotCompanyId
        );
    }

    private void put(Map<String, Object> properties, String key, Object value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
