package com.modernice.integration.controller;

import com.modernice.integration.connector.infor.crm.InforSDataMapper;
import com.modernice.integration.connector.infor.crm.MockInforCrmClient;
import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@Profile("local")
@RequestMapping("/mock/infor-sdata/sdata/slx/dynamic/-")
public class MockInforSDataController {

    private final MockInforCrmClient mockInforCrmClient;
    private final InforSDataMapper mapper;

    public MockInforSDataController(MockInforCrmClient mockInforCrmClient, InforSDataMapper mapper) {
        this.mockInforCrmClient = mockInforCrmClient;
        this.mapper = mapper;
    }

    @GetMapping("/contacts('{contactId}')")
    public Mono<Map<String, Object>> getContact(@PathVariable String contactId) {
        return mockInforCrmClient.getContact(contactId)
                .switchIfEmpty(notFound("Contact", contactId))
                .map(this::contactResource);
    }

    @PutMapping("/contacts('{contactId}')")
    public Mono<Map<String, Object>> updateContact(
            @PathVariable String contactId,
            @RequestBody Map<String, Object> updates) {
        return mockInforCrmClient.getContact(contactId)
                .switchIfEmpty(notFound("Contact", contactId))
                .flatMap(current -> {
                    Map<String, Object> merged = contactResource(current);
                    merge(merged, updates);
                    InforContactDto mapped = mapper.fromSDataContact(
                            contactId,
                            merged,
                            InforSDataMapper.nested(merged, "Address"),
                            InforSDataMapper.nested(merged, "Account")
                    );
                    Map<String, Object> address = InforSDataMapper.nested(merged, "Address");
                    InforContactDto updated = new InforContactDto(
                            mapped.crmContactId(),
                            mapped.firstName(),
                            mapped.lastName(),
                            mapped.account(),
                            mapped.miCustomerId(),
                            mapped.crmAccountId(),
                            addressValue(address, "City", current.city()),
                            addressValue(address, "State", current.state()),
                            mapped.workPhone(),
                            mapped.mobile(),
                            mapped.email(),
                            mapped.acctManager(),
                            mapped.subType(),
                            mapped.erpTerritory(),
                            mapped.createDate(),
                            Instant.now(),
                            mapped.owner(),
                            mapped.erpContactNo(),
                            mapped.hubSpotRecordId() != null
                                    ? mapped.hubSpotRecordId()
                                    : current.hubSpotRecordId()
                    );
                    return mockInforCrmClient.upsertContact(updated)
                            .thenReturn(contactResource(updated));
                });
    }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createContact(@RequestBody Map<String, Object> properties) {
        String contactId = "C-MOCK-" + UUID.randomUUID();
        InforContactDto contact = mapper.fromSDataContact(
                contactId,
                properties,
                InforSDataMapper.nested(properties, "Address"),
                InforSDataMapper.nested(properties, "Account")
        );
        return mockInforCrmClient.upsertContact(contact).thenReturn(contactResource(contact));
    }

    @GetMapping("/accounts('{accountId}')")
    public Mono<Map<String, Object>> getAccount(@PathVariable String accountId) {
        return mockInforCrmClient.getAccount(accountId)
                .switchIfEmpty(notFound("Account", accountId))
                .map(this::accountResource);
    }

    @PutMapping("/accounts('{accountId}')")
    public Mono<Map<String, Object>> updateAccount(
            @PathVariable String accountId,
            @RequestBody Map<String, Object> updates) {
        return mockInforCrmClient.getAccount(accountId)
                .switchIfEmpty(notFound("Account", accountId))
                .flatMap(current -> {
                    Map<String, Object> merged = accountResource(current);
                    merge(merged, updates);
                    InforAccountDto mapped = mapper.fromSDataAccount(
                            accountId,
                            merged,
                            InforSDataMapper.nested(merged, "Address")
                    );
                    Map<String, Object> address = InforSDataMapper.nested(merged, "Address");
                    InforAccountDto updated = new InforAccountDto(
                            mapped.customerId(),
                            mapped.accountId(),
                            mapped.name(),
                            addressValue(address, "Address1", current.address1()),
                            addressValue(address, "Address2", current.address2()),
                            addressValue(address, "City", current.city()),
                            addressValue(address, "State", current.state()),
                            addressValue(address, "PostalCode", current.postalCode()),
                            mapped.mainPhone(),
                            mapped.type(),
                            mapped.subType(),
                            mapped.status(),
                            mapped.erpTerritory(),
                            mapped.accountManager(),
                            mapped.customerType(),
                            mapped.owner(),
                            mapped.hubSpotCompanyId() != null
                                    ? mapped.hubSpotCompanyId()
                                    : current.hubSpotCompanyId()
                    );
                    return mockInforCrmClient.upsertAccount(updated)
                            .thenReturn(accountResource(updated));
                });
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createAccount(@RequestBody Map<String, Object> properties) {
        String accountId = "A-MOCK-" + UUID.randomUUID();
        InforAccountDto account = mapper.fromSDataAccount(
                accountId,
                properties,
                InforSDataMapper.nested(properties, "Address")
        );
        return mockInforCrmClient.upsertAccount(account).thenReturn(accountResource(account));
    }

    @GetMapping("/addresses('{addressId}')")
    public Mono<Map<String, Object>> getAddress(@PathVariable String addressId) {
        if (addressId.startsWith("contact-")) {
            String contactId = addressId.substring("contact-".length());
            return mockInforCrmClient.getContact(contactId)
                    .switchIfEmpty(notFound("Address", addressId))
                    .map(contact -> addressResource(
                            null, null, contact.city(), contact.state(), null
                    ));
        }
        if (addressId.startsWith("account-")) {
            String accountId = addressId.substring("account-".length());
            return mockInforCrmClient.getAccount(accountId)
                    .switchIfEmpty(notFound("Address", addressId))
                    .map(account -> addressResource(
                            account.address1(), account.address2(), account.city(),
                            account.state(), account.postalCode()
                    ));
        }
        return notFound("Address", addressId);
    }

    @PutMapping("/addresses('{addressId}')")
    public Mono<Map<String, Object>> updateAddress(
            @PathVariable String addressId,
            @RequestBody Map<String, Object> updates) {
        if (addressId.startsWith("contact-")) {
            String contactId = addressId.substring("contact-".length());
            return mockInforCrmClient.getContact(contactId)
                    .switchIfEmpty(notFound("Address", addressId))
                    .flatMap(current -> {
                        Map<String, Object> merged = addressResource(
                                null, null, current.city(), current.state(), null
                        );
                        merge(merged, updates);
                        InforContactDto updated = new InforContactDto(
                                current.crmContactId(),
                                current.firstName(),
                                current.lastName(),
                                current.account(),
                                current.miCustomerId(),
                                current.crmAccountId(),
                                string(merged, "City"),
                                string(merged, "State"),
                                current.workPhone(),
                                current.mobile(),
                                current.email(),
                                current.acctManager(),
                                current.subType(),
                                current.erpTerritory(),
                                current.createDate(),
                                current.modifyDate(),
                                current.owner(),
                                current.erpContactNo(),
                                current.hubSpotRecordId()
                        );
                        return mockInforCrmClient.upsertContact(updated)
                                .thenReturn(addressResource(
                                        null, null, updated.city(), updated.state(), null
                                ));
                    });
        }
        if (addressId.startsWith("account-")) {
            String accountId = addressId.substring("account-".length());
            return mockInforCrmClient.getAccount(accountId)
                    .switchIfEmpty(notFound("Address", addressId))
                    .flatMap(current -> {
                        Map<String, Object> merged = addressResource(
                                current.address1(), current.address2(), current.city(),
                                current.state(), current.postalCode()
                        );
                        merge(merged, updates);
                        InforAccountDto updated = new InforAccountDto(
                                current.customerId(),
                                current.accountId(),
                                current.name(),
                                string(merged, "Address1"),
                                string(merged, "Address2"),
                                string(merged, "City"),
                                string(merged, "State"),
                                string(merged, "PostalCode"),
                                current.mainPhone(),
                                current.type(),
                                current.subType(),
                                current.status(),
                                current.erpTerritory(),
                                current.accountManager(),
                                current.customerType(),
                                current.owner(),
                                current.hubSpotCompanyId()
                        );
                        return mockInforCrmClient.upsertAccount(updated)
                                .thenReturn(addressResource(
                                        updated.address1(), updated.address2(), updated.city(),
                                        updated.state(), updated.postalCode()
                                ));
                    });
        }
        return notFound("Address", addressId);
    }

    private Map<String, Object> contactResource(InforContactDto contact) {
        Map<String, Object> resource = new LinkedHashMap<>(mapper.toSDataContact(contact));
        resource.put("$key", contact.crmContactId());
        resource.put("$url", "contacts('" + contact.crmContactId() + "')");
        resource.put("$updated", Instant.now().toString());
        resource.put("Address", Map.of("$key", "contact-" + contact.crmContactId()));
        return resource;
    }

    private Map<String, Object> accountResource(InforAccountDto account) {
        Map<String, Object> resource = new LinkedHashMap<>(mapper.toSDataAccount(account));
        resource.put("$key", account.accountId());
        resource.put("$url", "accounts('" + account.accountId() + "')");
        resource.put("$updated", Instant.now().toString());
        resource.put("Address", Map.of("$key", "account-" + account.accountId()));
        return resource;
    }

    private Map<String, Object> addressResource(
            String address1,
            String address2,
            String city,
            String state,
            String postalCode) {
        Map<String, Object> resource = new LinkedHashMap<>();
        put(resource, "Address1", address1);
        put(resource, "Address2", address2);
        put(resource, "City", city);
        put(resource, "State", state);
        put(resource, "PostalCode", postalCode);
        return resource;
    }

    private static void merge(Map<String, Object> target, Map<String, Object> updates) {
        updates.forEach((key, value) -> {
            if (value instanceof Map<?, ?> nestedUpdate) {
                Map<String, Object> nested = new LinkedHashMap<>();
                Object current = target.get(key);
                if (current instanceof Map<?, ?> currentMap) {
                    currentMap.forEach((nestedKey, nestedValue) ->
                            nested.put(String.valueOf(nestedKey), nestedValue));
                }
                Map<String, Object> converted = new LinkedHashMap<>();
                nestedUpdate.forEach((nestedKey, nestedValue) ->
                        converted.put(String.valueOf(nestedKey), nestedValue));
                merge(nested, converted);
                target.put(key, nested);
            } else {
                target.put(key, value);
            }
        });
    }

    private static void put(Map<String, Object> values, String name, Object value) {
        if (value != null) {
            values.put(name, value);
        }
    }

    private static String string(Map<String, Object> values, String name) {
        Object value = values.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private static String addressValue(Map<String, Object> address, String name, String currentValue) {
        return address != null && address.containsKey(name)
                ? string(address, name)
                : currentValue;
    }

    private static <T> Mono<T> notFound(String entity, String id) {
        return Mono.error(new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Mock INFOR SData " + entity.toLowerCase() + " not found: " + id
        ));
    }
}
