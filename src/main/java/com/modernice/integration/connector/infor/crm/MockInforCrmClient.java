package com.modernice.integration.connector.infor.crm;

import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
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
    private final ConcurrentMap<String, String> crmToHubSpotIds = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeDefaultContacts() {
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
}
