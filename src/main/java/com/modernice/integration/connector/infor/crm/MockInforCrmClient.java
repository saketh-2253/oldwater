package com.modernice.integration.connector.infor.crm;

import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Profile("local")
public class MockInforCrmClient implements InforCrmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockInforCrmClient.class);
    private final ConcurrentMap<String, InforContactDto> contacts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, InforAccountDto> accounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> crmToHubSpotIds = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeDefaultContacts() {
        accounts.putIfAbsent("A500", new InforAccountDto(
                "12345",
                "A500",
                "Modern Ice Customer",
                "123 Main Street",
                null,
                "Cincinnati",
                "OH",
                "45202",
                "5135551000",
                null,
                "Customer",
                "Active",
                "100",
                "U738UA00000M",
                "DI",
                "U738UA00002W",
                null
        ));
        contacts.putIfAbsent("103", new InforContactDto(
                "103",
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
                Instant.parse("2026-05-06T14:30:00Z"),
                Instant.now(),
                "U738UA00002W",
                "4321",
                null
        ));
    }

    @Override
    public Mono<InforContactDto> getContact(String crmContactId) {
        return Mono.justOrEmpty(contacts.get(crmContactId));
    }

    @Override
    public Mono<Void> setContact(InforContactDto contact) {
        return Mono.fromRunnable(() -> {
            contacts.put(contact.crmContactId(), contact);
            if (contact.hubSpotRecordId() != null) {
                crmToHubSpotIds.put(contact.crmContactId(), contact.hubSpotRecordId());
            }
            LOGGER.info("INFOR mock set contact {}: {}", contact.crmContactId(), contact);
        });
    }

    @Override
    public Mono<Void> upsertContact(InforContactDto contact) {
        return setContact(contact);
    }

    @Override
    public Mono<Void> updateHubSpotRecordId(String crmContactId, String hubSpotRecordId) {
        return Mono.fromRunnable(() -> {
            crmToHubSpotIds.put(crmContactId, hubSpotRecordId);
            InforContactDto current = contacts.get(crmContactId);
            if (current != null) {
                contacts.put(crmContactId, new InforContactDto(
                        current.crmContactId(),
                        current.firstName(),
                        current.lastName(),
                        current.account(),
                        current.miCustomerId(),
                        current.crmAccountId(),
                        current.city(),
                        current.state(),
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
                        hubSpotRecordId
                ));
            }
            LOGGER.info("INFOR mock saved HubSpot ID {} for contact {}", hubSpotRecordId, crmContactId);
        });
    }

    @Override
    public Mono<InforAccountDto> getAccount(String accountId) {
        return Mono.justOrEmpty(accounts.get(accountId));
    }

    @Override
    public Mono<Void> upsertAccount(InforAccountDto account) {
        return Mono.fromRunnable(() -> accounts.compute(account.accountId(), (accountId, current) -> {
            if (current == null || account.hubSpotCompanyId() != null) {
                return account;
            }
            return new InforAccountDto(
                    account.customerId(),
                    account.accountId(),
                    account.name(),
                    account.address1(),
                    account.address2(),
                    account.city(),
                    account.state(),
                    account.postalCode(),
                    account.mainPhone(),
                    account.type(),
                    account.subType(),
                    account.status(),
                    account.erpTerritory(),
                    account.accountManager(),
                    account.customerType(),
                    account.owner(),
                    current.hubSpotCompanyId()
            );
        }));
    }

    @Override
    public Mono<Void> updateHubSpotCompanyId(String accountId, String hubSpotCompanyId) {
        return Mono.fromRunnable(() -> {
            InforAccountDto current = accounts.get(accountId);
            if (current != null) {
                accounts.put(accountId, new InforAccountDto(
                        current.customerId(),
                        current.accountId(),
                        current.name(),
                        current.address1(),
                        current.address2(),
                        current.city(),
                        current.state(),
                        current.postalCode(),
                        current.mainPhone(),
                        current.type(),
                        current.subType(),
                        current.status(),
                        current.erpTerritory(),
                        current.accountManager(),
                        current.customerType(),
                        current.owner(),
                        hubSpotCompanyId
                ));
            }
            LOGGER.info("INFOR mock saved HubSpot company ID {} for account {}", hubSpotCompanyId, accountId);
        });
    }
}
