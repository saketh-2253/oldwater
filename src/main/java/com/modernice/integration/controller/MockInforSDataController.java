package com.modernice.integration.controller;

import com.modernice.integration.connector.infor.crm.InforSDataMapper;
import com.modernice.integration.connector.infor.crm.MockInforCrmClient;
import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@Profile("local")
@RequestMapping("/mock/infor-sdata/sdata/slx/dynamic/-")
public class MockInforSDataController {

    private static final String MOCK_SDATA_ROOT = "/mock/infor-sdata/sdata/slx/dynamic/-/";
    private static final Pattern ACCOUNT_CONTACTS_FILTER =
            Pattern.compile("^\\s*Account\\.Id\\s+eq\\s+'((?:''|[^'])*)'\\s*$");
    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";

    private final MockInforCrmClient mockInforCrmClient;
    private final InforSDataMapper mapper;

    public MockInforSDataController(MockInforCrmClient mockInforCrmClient, InforSDataMapper mapper) {
        this.mockInforCrmClient = mockInforCrmClient;
        this.mapper = mapper;
    }

    @GetMapping("/contacts")
    public Mono<Map<String, Object>> listContacts(
            @RequestParam(required = false) String where,
            ServerHttpRequest request) {
        String accountId = accountIdFromWhere(where);
        return mockInforCrmClient.listContacts()
                .filter(contact -> accountId == null || accountId.equals(contact.crmAccountId()))
                .map(contact -> contactResource(contact, request))
                .collectList()
                .map(resources -> feed(request.getURI().toString(), resources));
    }

    @GetMapping("/contacts('{contactId}')")
    public Mono<Map<String, Object>> getContact(
            @PathVariable String contactId,
            ServerHttpRequest request) {
        return mockInforCrmClient.getContact(contactId)
                .switchIfEmpty(notFound("Contact", contactId))
                .map(contact -> contactResource(contact, request));
    }

    @PutMapping("/contacts('{contactId}')")
    public Mono<Map<String, Object>> updateContact(
            @PathVariable String contactId,
            @RequestBody Map<String, Object> updates,
            ServerHttpRequest request) {
        return mockInforCrmClient.getContact(contactId)
                .flatMap(current -> {
                    Map<String, Object> merged = contactResource(current, request);
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
                            .thenReturn(contactResource(updated, request));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    InforContactDto created = mapper.fromSDataContact(
                            contactId,
                            updates,
                            InforSDataMapper.nested(updates, "Address"),
                            InforSDataMapper.nested(updates, "Account")
                    );
                    return mockInforCrmClient.upsertContact(created)
                            .thenReturn(contactResource(created, request));
                }));
    }

    @PostMapping("/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createContact(
            @RequestBody Map<String, Object> properties,
            ServerHttpRequest request) {
        String contactId = "C-MOCK-" + UUID.randomUUID();
        InforContactDto contact = mapper.fromSDataContact(
                contactId,
                properties,
                InforSDataMapper.nested(properties, "Address"),
                InforSDataMapper.nested(properties, "Account")
        );
        return mockInforCrmClient.upsertContact(contact).thenReturn(contactResource(contact, request));
    }

    @GetMapping("/accounts")
    public Mono<Map<String, Object>> listAccounts(
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count,
            ServerHttpRequest request) {
        if (startIndex < 1 || count < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startIndex and count must be positive integers");
        }
        return mockInforCrmClient.listAccounts()
                .map(account -> accountResource(account, request))
                .collectList()
                .map(resources -> pagedFeed(request.getURI().toString(), resources, startIndex, count));
    }

    @GetMapping("/accounts('{accountId}')")
    public Mono<Map<String, Object>> getAccount(
            @PathVariable String accountId,
            ServerHttpRequest request) {
        return mockInforCrmClient.getAccount(accountId)
                .switchIfEmpty(notFound("Account", accountId))
                .map(account -> accountResource(account, request));
    }

    @GetMapping("/accounts('{accountId}')/Addresses")
    public Mono<Map<String, Object>> listAccountAddresses(
            @PathVariable String accountId,
            ServerHttpRequest request) {
        return mockInforCrmClient.getAccount(accountId)
                .switchIfEmpty(notFound("Account", accountId))
                .map(account -> addressEntry(
                                "account-" + accountId,
                                account.address1(),
                                account.address2(),
                                account.city(),
                                account.state(),
                                account.postalCode(),
                                request
                        ))
                .map(address -> feed(request.getURI().toString(), java.util.List.of(address)));
    }

    @PutMapping("/accounts('{accountId}')")
    public Mono<Map<String, Object>> updateAccount(
            @PathVariable String accountId,
            @RequestBody Map<String, Object> updates,
            ServerHttpRequest request) {
        return mockInforCrmClient.getAccount(accountId)
                .flatMap(current -> {
                    Map<String, Object> merged = accountResource(current, request);
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
                            .thenReturn(accountResource(updated, request));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    InforAccountDto created = mapper.fromSDataAccount(
                            accountId,
                            updates,
                            InforSDataMapper.nested(updates, "Address")
                    );
                    return mockInforCrmClient.upsertAccount(created)
                            .thenReturn(accountResource(created, request));
                }));
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, Object>> createAccount(
            @RequestBody Map<String, Object> properties,
            ServerHttpRequest request) {
        String accountId = "A-MOCK-" + UUID.randomUUID();
        InforAccountDto account = mapper.fromSDataAccount(
                accountId,
                properties,
                InforSDataMapper.nested(properties, "Address")
        );
        return mockInforCrmClient.upsertAccount(account).thenReturn(accountResource(account, request));
    }

    @GetMapping("/addresses")
    public Mono<Map<String, Object>> listAddresses(ServerHttpRequest request) {
        Flux<Map<String, Object>> accountAddresses = mockInforCrmClient.listAccounts()
                .map(account -> addressEntry(
                        "account-" + account.accountId(),
                        account.address1(),
                        account.address2(),
                        account.city(),
                        account.state(),
                        account.postalCode(),
                        request
                ));
        Flux<Map<String, Object>> contactAddresses = mockInforCrmClient.listContacts()
                .map(contact -> addressEntry(
                        "contact-" + contact.crmContactId(),
                        null,
                        null,
                        contact.city(),
                        contact.state(),
                        null,
                        request
                ));
        return Flux.concat(accountAddresses, contactAddresses)
                .collectList()
                .map(resources -> feed(request.getURI().toString(), resources));
    }

    @GetMapping("/addresses('{addressId}')")
    public Mono<Map<String, Object>> getAddress(
            @PathVariable String addressId,
            ServerHttpRequest request) {
        if (addressId.startsWith("contact-")) {
            String contactId = addressId.substring("contact-".length());
            return mockInforCrmClient.getContact(contactId)
                    .switchIfEmpty(notFound("Address", addressId))
                    .map(contact -> addressEntry(
                            addressId, null, null, contact.city(), contact.state(), null, request));
        }
        if (addressId.startsWith("account-")) {
            String accountId = addressId.substring("account-".length());
            return mockInforCrmClient.getAccount(accountId)
                    .switchIfEmpty(notFound("Address", addressId))
                    .map(account -> addressEntry(
                            addressId, account.address1(), account.address2(), account.city(),
                            account.state(), account.postalCode(), request));
        }
        return notFound("Address", addressId);
    }

    @PutMapping("/addresses('{addressId}')")
    public Mono<Map<String, Object>> updateAddress(
            @PathVariable String addressId,
            @RequestBody Map<String, Object> updates,
            ServerHttpRequest request) {
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
                                .thenReturn(addressEntry(addressId, null, null, updated.city(),
                                        updated.state(), null, request));
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
                                .thenReturn(addressEntry(addressId, updated.address1(), updated.address2(),
                                        updated.city(), updated.state(), updated.postalCode(), request));
                    });
        }
        return notFound("Address", addressId);
    }

    private Map<String, Object> contactResource(InforContactDto contact, ServerHttpRequest request) {
        Map<String, Object> resource = new LinkedHashMap<>(mapper.toSDataContact(contact));
        resource.put("$key", contact.crmContactId());
        resource.put("$url", sdataRoot(request) + "contacts('" + contact.crmContactId() + "')");
        resource.put("$updated", Instant.now().toString());
        resource.put("Address", addressReference("contact-" + contact.crmContactId(), request));
        return resource;
    }

    private Map<String, Object> accountResource(InforAccountDto account, ServerHttpRequest request) {
        Map<String, Object> resource = new LinkedHashMap<>(mapper.toSDataAccount(account));
        resource.put("$key", account.accountId());
        resource.put("$url", sdataRoot(request) + "accounts('" + account.accountId() + "')");
        resource.put("$updated", Instant.now().toString());
        resource.put("Address", addressReference("account-" + account.accountId(), request));
        resource.put("Addresses", Map.of(
                "$url", sdataRoot(request) + "accounts('" + account.accountId() + "')/Addresses"));
        resource.put("Contacts", Map.of("$url", contactsUrl(account.accountId(), request)));
        return resource;
    }

    private Map<String, Object> addressReference(String addressId, ServerHttpRequest request) {
        Map<String, Object> reference = new LinkedHashMap<>();
        reference.put("$key", addressId);
        reference.put("$url", sdataRoot(request) + "addresses('" + addressId + "')");
        reference.put("$uuid", ZERO_UUID);
        reference.put("$lookup", sdataRoot(request) + "addresses?format=json");
        return reference;
    }

    private Map<String, Object> addressEntry(
            String addressId,
            String address1,
            String address2,
            String city,
            String state,
            String postalCode,
            ServerHttpRequest request) {
        Map<String, Object> resource = addressResource(address1, address2, city, state, postalCode);
        resource.put("$key", addressId);
        resource.put("$url", sdataRoot(request) + "addresses('" + addressId + "')");
        resource.put("$uuid", ZERO_UUID);
        resource.put("$updated", Instant.now().toString());
        return resource;
    }

    private Map<String, Object> feed(String url, java.util.List<Map<String, Object>> resources) {
        return pagedFeed(url, resources, 1, resources.size());
    }

    private Map<String, Object> pagedFeed(
            String url,
            java.util.List<Map<String, Object>> allResources,
            int startIndex,
            int count) {
        int fromIndex = Math.min(startIndex - 1, allResources.size());
            int toIndex = fromIndex + Math.min(count, allResources.size() - fromIndex);
        java.util.List<Map<String, Object>> resources = allResources.subList(fromIndex, toIndex);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("$url", url);
        result.put("$totalResults", allResources.size());
        result.put("$startIndex", startIndex);
        result.put("$itemsPerPage", resources.size());
        result.put("$resources", resources);
        return result;
    }

    private String contactsUrl(String accountId, ServerHttpRequest request) {
        String where = "Account.Id eq '" + accountId.replace("'", "''") + "'";
        String encodedWhere = URLEncoder.encode(where, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%27", "'");
        return sdataRoot(request) + "contacts?format=json&where=" + encodedWhere;
    }

    private static String accountIdFromWhere(String where) {
        if (where == null || where.isBlank()) {
            return null;
        }
        Matcher matcher = ACCOUNT_CONTACTS_FILTER.matcher(where);
        if (!matcher.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported contacts filter; expected Account.Id eq '<accountId>'");
        }
        return matcher.group(1).replace("''", "'");
    }

    private static String sdataRoot(ServerHttpRequest request) {
        URI uri = request.getURI();
        return uri.getScheme() + "://" + uri.getRawAuthority() + MOCK_SDATA_ROOT;
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
