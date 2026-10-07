package com.modernice.integration.connector.hubspot.mapper;

import com.modernice.integration.connector.hubspot.dto.HubSpotContactRequest;
import com.modernice.integration.domain.model.Contact;
import com.modernice.integration.mapping.ValueMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HubSpotContactMapper {

    private final ValueMappingService valueMappingService;

    public HubSpotContactRequest toHubSpot(Contact contact) {
        Map<String, Object> properties = new LinkedHashMap<>();

        put(properties, "firstname", contact.firstName());
        put(properties, "lastname", contact.lastName());
        put(properties, "crm_contact_id", contact.crmContactId());
        put(properties, "company", contact.accountName());
        put(properties, "mi_customer_id", contact.miCustomerId());
        put(properties, "crm_account_id", contact.crmAccountId());
        put(properties, "city", contact.city());
        put(properties, "state", contact.state());
        put(properties, "work_phone", contact.workPhone());
        put(properties, "infor_mobile_phone", contact.mobilePhone());
        put(properties, "email", contact.email());
        put(properties, "account_manager", contact.accountManager());
        put(properties, "subtype", contact.subType());
        put(properties, "erp_territory",
                valueMappingService.map("ERP_TERRITORY", contact.erpTerritory()));
        put(properties, "infor_created_date", contact.createdDate());
        put(properties, "owner_infor", contact.owner());

        // Verify final HubSpot internal property name in sandbox before production.
        put(properties, "erp_contact_no", contact.erpContactNumber());

        return new HubSpotContactRequest(properties);
    }

    public Contact fromHubSpot(Map<String, Object> properties) {
        return new Contact(
                asString(properties.get("crm_contact_id")),
                asString(properties.get("firstname")),
                asString(properties.get("lastname")),
                asString(properties.get("company")),
                asString(properties.get("mi_customer_id")),
                asString(properties.get("crm_account_id")),
                asString(properties.get("city")),
                asString(properties.get("state")),
                asString(properties.get("work_phone")),
                asString(properties.get("infor_mobile_phone")),
                asString(properties.get("email")),
                asString(properties.get("account_manager")),
                asString(properties.get("subtype")),
                asString(properties.get("erp_territory")),
                null,
                null,
                asString(properties.get("owner_infor")),
                asString(properties.get("erp_contact_no")),
                null
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
