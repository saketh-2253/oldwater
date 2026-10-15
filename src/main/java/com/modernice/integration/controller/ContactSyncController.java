package com.modernice.integration.controller;

import com.modernice.integration.controller.dto.ContactSyncBatchRequest;
import com.modernice.integration.controller.dto.ContactSyncBatchResponse;
import com.modernice.integration.controller.dto.ContactSyncBatchItemResponse;
import com.modernice.integration.controller.dto.ContactSyncBatchStatusResponse;
import com.modernice.integration.controller.dto.AccountSyncBatchItemResponse;
import com.modernice.integration.controller.dto.AccountSyncBatchRequest;
import com.modernice.integration.controller.dto.AccountSyncBatchResponse;
import com.modernice.integration.controller.dto.AccountSyncBatchStatusResponse;
import com.modernice.integration.connector.hubspot.HubSpotClient;
import com.modernice.integration.connector.hubspot.dto.HubSpotCompanyRequest;
import com.modernice.integration.connector.hubspot.dto.HubSpotContactRequest;
import com.modernice.integration.connector.infor.crm.InforCrmClient;
import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import com.modernice.integration.workflow.ContactSyncWorkflow;
import com.modernice.integration.workflow.ContactSyncBatchService;
import com.modernice.integration.workflow.AccountSyncWorkflow;
import com.modernice.integration.workflow.AccountSyncBatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContactSyncController {

    private final ContactSyncWorkflow contactSyncWorkflow;
    private final ContactSyncBatchService contactSyncBatchService;
    private final AccountSyncWorkflow accountSyncWorkflow;
    private final AccountSyncBatchService accountSyncBatchService;
    private final InforCrmClient inforCrmClient;
    private final HubSpotClient hubSpotClient;

    @GetMapping("/hubspot/contacts/{hubSpotContactId}")
    public Mono<Map<String, Object>> getHubSpotContact(@PathVariable String hubSpotContactId) {
        return hubSpotClient.getContact(hubSpotContactId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "HubSpot contact not found: " + hubSpotContactId
                )))
                .map(properties -> {
                    Map<String, Object> response = new LinkedHashMap<>(properties);
                    response.put("id", hubSpotContactId);
                    return response;
                });
    }

    @PostMapping("/hubspot/contacts/{hubSpotContactId}")
    public Mono<Void> upsertHubSpotContact(
            @PathVariable String hubSpotContactId,
            @RequestParam Map<String, String> updates) {
        if (updates.isEmpty()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide at least one HubSpot contact property to update"
            ));
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        updates.forEach((name, value) -> properties.put(toHubSpotPropertyName(name), value));

        return hubSpotClient.getContact(hubSpotContactId)
            .switchIfEmpty(Mono.defer(() -> {
                if (properties.get("crm_contact_id") == null || properties.get("crm_contact_id").toString().isBlank()) {
                return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "crmContactId is required when creating a HubSpot contact"
                ));
                }
                return Mono.just(Map.<String, Object>of());
            }))
            .flatMap(existing -> {
                Map<String, Object> mergedProperties = new LinkedHashMap<>(existing);
                mergedProperties.putAll(properties);

                return hubSpotClient.upsertContact(
                        hubSpotContactId,
                        new HubSpotContactRequest(mergedProperties)
                    );
            });
    }

    private String toHubSpotPropertyName(String name) {
        return switch (name) {
            case "firstName", "firstname" -> "firstname";
            case "lastName", "lastname" -> "lastname";
            case "crmContactId" -> "crm_contact_id";
            case "account" -> "company";
            case "miCustomerId" -> "mi_customer_id";
            case "crmAccountId" -> "crm_account_id";
            case "workPhone" -> "work_phone";
            case "mobile" -> "infor_mobile_phone";
            case "acctManager" -> "account_manager";
            case "subType" -> "subtype";
            case "erpTerritory" -> "erp_territory";
            case "createDate" -> "infor_created_date";
            case "owner" -> "owner_infor";
            case "erpContactNo" -> "erp_contact_no";
            default -> name;
        };
    }

    @PostMapping("/hubspot/companies/{hubSpotCompanyId}")
    public Mono<Void> upsertHubSpotCompany(
            @PathVariable String hubSpotCompanyId,
            @RequestParam Map<String, String> updates) {
        if (updates.isEmpty()) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Provide at least one HubSpot company property to update"
            ));
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        updates.forEach((name, value) -> properties.put(toHubSpotCompanyPropertyName(name), value));

        return hubSpotClient.getCompany(hubSpotCompanyId)
                .switchIfEmpty(Mono.defer(() -> {
                    Object inforAccountId = properties.get("infor_account_id");
                    if (inforAccountId == null || inforAccountId.toString().isBlank()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "inforAccountId is required when creating a HubSpot company"
                        ));
                    }
                    return Mono.just(Map.<String, Object>of());
                }))
                .flatMap(existing -> {
                    Map<String, Object> mergedProperties = new LinkedHashMap<>(existing);
                    mergedProperties.putAll(properties);
                    return hubSpotClient.updateCompany(
                            hubSpotCompanyId,
                            new HubSpotCompanyRequest(mergedProperties)
                    );
                });
    }

    private String toHubSpotCompanyPropertyName(String name) {
        return switch (name) {
            case "customerId" -> "customer_id";
            case "address1" -> "address";
            case "postalCode" -> "zip";
            case "subType" -> "subtype";
            case "erpTerritory" -> "erp_territory";
            case "accountManager" -> "account_manager";
            case "customerType" -> "customer_type";
            case "owner", "inforOwner" -> "infor_owner";
            case "accountId", "inforAccountId" -> "infor_account_id";
            default -> name;
        };
    }

    @GetMapping("/infor/contacts/{crmContactId}")
    public Mono<InforContactDto> getMockInforContact(@PathVariable String crmContactId) {
        return inforCrmClient.getContact(crmContactId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "INFOR contact not found: " + crmContactId
                )));
    }

    @PostMapping("/infor/contacts/{crmContactId}")
    public Mono<Void> updateMockInforContact(
            @PathVariable String crmContactId,
            @RequestParam Map<String, String> updates) {
        return inforCrmClient.getContact(crmContactId)
            .switchIfEmpty(Mono.just(new InforContactDto(
                crmContactId,
                "John",
                "Smith",
                "Modern Ice Customer",
                "12345",
                "A500",
                "Cincinnati",
                "OH",
                "5135551000",
                "5135552000",
                "john.smith@example.com",
                "U738UA00000M",
                "Customer",
                "100",
                Instant.now(),
                Instant.now(),
                "U738UA00002W",
                "4321",
                null
            )))
                .map(current -> {
                    String firstName = updates.getOrDefault("firstName", current.firstName());
                    String lastName = updates.getOrDefault("lastName", current.lastName());
                    String email = updates.getOrDefault("email", current.email());
                    String city = updates.getOrDefault("city", current.city());
                    String state = updates.getOrDefault("state", current.state());
                    String account = updates.getOrDefault("account", current.account());
                    String miCustomerId = updates.getOrDefault("miCustomerId", current.miCustomerId());
                    String crmAccountId = updates.getOrDefault("crmAccountId", current.crmAccountId());
                    String workPhone = updates.getOrDefault("workPhone", current.workPhone());
                    String mobile = updates.getOrDefault("mobile", current.mobile());
                    String acctManager = updates.getOrDefault("acctManager", current.acctManager());
                    String subType = updates.getOrDefault("subType", current.subType());
                    String erpTerritory = updates.getOrDefault("erpTerritory", current.erpTerritory());
                    String owner = updates.getOrDefault("owner", current.owner());
                    String erpContactNo = updates.getOrDefault("erpContactNo", current.erpContactNo());
                    String hubSpotRecordId = updates.getOrDefault("hubSpotRecordId", current.hubSpotRecordId());

                    return new InforContactDto(
                            crmContactId,
                            firstName,
                            lastName,
                            account,
                            miCustomerId,
                            crmAccountId,
                            city,
                            state,
                            workPhone,
                            mobile,
                            email,
                            acctManager,
                            subType,
                            erpTerritory,
                            current.createDate() == null ? Instant.now() : current.createDate(),
                            Instant.now(),
                            owner,
                            erpContactNo,
                            hubSpotRecordId
                    );
                })
                .flatMap(inforCrmClient::setContact);
    }

    @PostMapping("/sync/contacts/{crmContactId}")
    public Mono<Void> syncContact(@PathVariable String crmContactId) {
        return contactSyncWorkflow.syncInforContactToHubSpot(crmContactId);
    }

    @PostMapping("/sync/contacts/batch")
    public ResponseEntity<ContactSyncBatchResponse> syncContactBatch(
            @Valid @RequestBody ContactSyncBatchRequest request) {
        ContactSyncBatchStatusResponse batch = contactSyncBatchService.submit(request.contactIds());
        String statusUrl = "/api/sync/batches/" + batch.batchId();
        return ResponseEntity.accepted().body(new ContactSyncBatchResponse(
                batch.batchId(),
                statusUrl,
                batch.totalContacts()
        ));
    }

    @GetMapping("/sync/batches/{batchId}")
    public ContactSyncBatchStatusResponse getSyncBatchStatus(@PathVariable UUID batchId) {
        return contactSyncBatchService.getStatus(batchId);
    }

    @GetMapping("/sync/batches/{batchId}/items")
    public Page<ContactSyncBatchItemResponse> getSyncBatchItems(
            @PathVariable UUID batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return contactSyncBatchService.getItems(batchId, page, size);
    }

    @GetMapping("/infor/accounts/{accountId}")
    public Mono<InforAccountDto> getMockInforAccount(@PathVariable String accountId) {
        return inforCrmClient.getAccount(accountId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "INFOR account not found: " + accountId
                )));
    }

    @PostMapping("/infor/accounts/{accountId}")
    public Mono<Void> upsertMockInforAccount(
            @PathVariable String accountId,
            @RequestBody InforAccountDto account) {
        if (!accountId.equals(account.accountId())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Path accountId must match accountId in request body"
            ));
        }
        return inforCrmClient.upsertAccount(account);
    }

    @GetMapping("/hubspot/companies/{hubSpotCompanyId}")
    public Mono<Map<String, Object>> getHubSpotCompany(@PathVariable String hubSpotCompanyId) {
        return hubSpotClient.getCompany(hubSpotCompanyId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "HubSpot company not found: " + hubSpotCompanyId
                )))
                .map(properties -> {
                    Map<String, Object> response = new LinkedHashMap<>(properties);
                    response.put("id", hubSpotCompanyId);
                    return response;
                });
    }

    @PostMapping("/sync/accounts/{accountId}")
    public Mono<Void> syncAccount(@PathVariable String accountId) {
        return accountSyncWorkflow.syncInforAccountToHubSpot(accountId);
    }

    @PostMapping({
            "/sync/hubspot/companies/{hubSpotCompanyId}",
            "/hubspot/companies/{hubSpotCompanyId}/sync-to-infor"
    })
    public Mono<Void> syncHubSpotCompany(@PathVariable String hubSpotCompanyId) {
        return accountSyncWorkflow.syncHubSpotCompanyToInfor(hubSpotCompanyId);
    }

    @PostMapping("/sync/accounts/batch")
    public ResponseEntity<AccountSyncBatchResponse> syncAccountBatch(
            @Valid @RequestBody AccountSyncBatchRequest request) {
        AccountSyncBatchStatusResponse batch = accountSyncBatchService.submit(request.accountIds());
        String statusUrl = "/api/sync/account-batches/" + batch.batchId();
        return ResponseEntity.accepted().body(new AccountSyncBatchResponse(
                batch.batchId(),
                statusUrl,
                batch.totalAccounts()
        ));
    }

    @GetMapping("/sync/account-batches/{batchId}")
    public AccountSyncBatchStatusResponse getAccountSyncBatchStatus(@PathVariable UUID batchId) {
        return accountSyncBatchService.getStatus(batchId);
    }

    @GetMapping("/sync/account-batches/{batchId}/items")
    public Page<AccountSyncBatchItemResponse> getAccountSyncBatchItems(
            @PathVariable UUID batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return accountSyncBatchService.getItems(batchId, page, size);
    }

    @PostMapping({
            "/sync/hubspot/contacts/{hubSpotContactId}",
            "/hubspot/contacts/{hubSpotContactId}/sync-to-infor"
    })
    public Mono<Void> syncHubSpotContact(@PathVariable String hubSpotContactId) {
        return contactSyncWorkflow.syncHubSpotContactToInfor(hubSpotContactId);
    }
}
