package com.modernice.integration.connector.infor.crm;

import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InforSDataMapper {

    private final InforSDataProperties.Fields fields;

    public InforSDataMapper(InforSDataProperties properties) {
        this.fields = properties.getFields();
    }

    public Map<String, Object> toSDataContact(InforContactDto contact) {
        Map<String, Object> properties = new LinkedHashMap<>();
        put(properties, "FirstName", contact.firstName());
        put(properties, "LastName", contact.lastName());
        put(properties, "AccountName", contact.account());
        put(properties, fields.getContactCustomerId(), contact.miCustomerId());
        put(properties, "Email", contact.email());
        put(properties, "WorkPhone", contact.workPhone());
        put(properties, "Mobile", contact.mobile());
        put(properties, fields.getContactErpTerritory(), contact.erpTerritory());
        put(properties, "SubType", contact.subType());
        put(properties, "CreateDate", contact.createDate());
        put(properties, "ModifyDate", contact.modifyDate());
        put(properties, fields.getContactErpContactNumber(), contact.erpContactNo());
        put(properties, fields.getContactHubSpotId(), contact.hubSpotRecordId());
        putReference(properties, "Account", contact.crmAccountId());
        putReference(properties, "AccountManager", contact.acctManager());
        putReference(properties, "Owner", contact.owner());
        putAddress(properties, null, null, contact.city(), contact.state(), null);
        return properties;
    }

    public Map<String, Object> toSDataAccount(InforAccountDto account) {
        Map<String, Object> properties = new LinkedHashMap<>();
        put(properties, fields.getAccountCustomerId(), account.customerId());
        put(properties, "AccountName", account.name());
        put(properties, "MainPhone", account.mainPhone());
        put(properties, "Type", account.type());
        put(properties, "SubType", account.subType());
        put(properties, "Status", account.status());
        put(properties, "ErpTerritory", account.erpTerritory());
        put(properties, "ErpCustType", account.customerType());
        put(properties, fields.getAccountHubSpotId(), account.hubSpotCompanyId());
        putReference(properties, "AccountManager", account.accountManager());
        putReference(properties, "Owner", account.owner());
        putAddress(properties, account.address1(), account.address2(), account.city(),
                account.state(), account.postalCode());
        return properties;
    }

    public InforContactDto fromSDataContact(
            String contactId,
            Map<String, Object> properties,
            Map<String, Object> address,
            Map<String, Object> account) {
        Map<String, Object> addressProperties = address != null ? address : nested(properties, "Address");
        Map<String, Object> accountProperties = account != null ? account : nested(properties, "Account");
        String customerId = string(properties.get(fields.getContactCustomerId()));
        if (customerId == null && accountProperties != null) {
            customerId = string(accountProperties.get(fields.getAccountCustomerId()));
        }
        return new InforContactDto(
                contactId,
                string(properties.get("FirstName")),
                string(properties.get("LastName")),
                string(properties.get("AccountName")),
                customerId,
                referenceKey(properties.get("Account")),
                string(value(addressProperties, "City")),
                string(value(addressProperties, "State")),
                string(properties.get("WorkPhone")),
                string(properties.get("Mobile")),
                string(properties.get("Email")),
                referenceKey(properties.get("AccountManager")),
                string(properties.get("SubType")),
                string(properties.get(fields.getContactErpTerritory())),
                instant(properties.get("CreateDate")),
                instant(properties.get("ModifyDate")),
                referenceKey(properties.get("Owner")),
                string(properties.get(fields.getContactErpContactNumber())),
                string(properties.get(fields.getContactHubSpotId()))
        );
    }

    public InforAccountDto fromSDataAccount(
            String accountId,
            Map<String, Object> properties,
            Map<String, Object> address) {
        Map<String, Object> addressProperties = address != null ? address : nested(properties, "Address");
        return new InforAccountDto(
                string(properties.get(fields.getAccountCustomerId())),
                accountId,
                string(properties.get("AccountName")),
                string(value(addressProperties, "Address1")),
                string(value(addressProperties, "Address2")),
                string(value(addressProperties, "City")),
                string(value(addressProperties, "State")),
                string(value(addressProperties, "PostalCode")),
                string(properties.get("MainPhone")),
                string(properties.get("Type")),
                string(properties.get("SubType")),
                string(properties.get("Status")),
                string(properties.get("ErpTerritory")),
                referenceKey(properties.get("AccountManager")),
                string(properties.get("ErpCustType")),
                referenceKey(properties.get("Owner")),
                string(properties.get(fields.getAccountHubSpotId()))
        );
    }

    public static String referenceKey(Object value) {
        if (value instanceof Map<?, ?> reference) {
            return string(reference.get("$key"));
        }
        return string(value);
    }

    public static Map<String, Object> nested(Map<String, Object> properties, String name) {
        Object value = properties.get(name);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> result.put(String.valueOf(key), nestedValue));
            return result;
        }
        return null;
    }

    private static Object value(Map<String, Object> properties, String name) {
        return properties == null ? null : properties.get(name);
    }

    private static void putAddress(
            Map<String, Object> properties,
            String address1,
            String address2,
            String city,
            String state,
            String postalCode) {
        Map<String, Object> address = new LinkedHashMap<>();
        put(address, "Address1", address1);
        put(address, "Address2", address2);
        put(address, "City", city);
        put(address, "State", state);
        put(address, "PostalCode", postalCode);
        if (!address.isEmpty()) {
            properties.put("Address", address);
        }
    }

    private static void putReference(Map<String, Object> properties, String name, String key) {
        if (key != null && !key.isBlank()) {
            properties.put(name, Map.of("$key", key));
        }
    }

    private static void put(Map<String, Object> properties, String name, Object value) {
        if (value != null) {
            properties.put(name, value);
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Instant instant(Object value) {
        String text = string(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.startsWith("/Date(") && text.endsWith(")/")) {
            String milliseconds = text.substring(6, text.length() - 2);
            int timezoneOffset = milliseconds.indexOf('+', 1);
            if (timezoneOffset < 0) {
                timezoneOffset = milliseconds.indexOf('-', 1);
            }
            if (timezoneOffset >= 0) {
                milliseconds = milliseconds.substring(0, timezoneOffset);
            }
            return Instant.ofEpochMilli(Long.parseLong(milliseconds));
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException ignoredOffset) {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            }
        }
    }
}
