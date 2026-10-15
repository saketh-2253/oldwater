package com.modernice.integration.workflow;

import com.modernice.integration.connector.hubspot.HubSpotClient;
import com.modernice.integration.connector.hubspot.mapper.HubSpotContactMapper;
import com.modernice.integration.connector.infor.crm.InforCrmClient;
import com.modernice.integration.connector.infor.crm.mapper.InforContactMapper;
import com.modernice.integration.identity.ContactIdentityService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContactSyncWorkflow {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContactSyncWorkflow.class);

    private final InforCrmClient inforCrmClient;
    private final InforContactMapper inforContactMapper;
    private final HubSpotContactMapper hubSpotContactMapper;
    private final HubSpotClient hubSpotClient;
    private final ContactIdentityService contactIdentityService;

    public Mono<Void> syncInforContactToHubSpot(String crmContactId) {
        return inforCrmClient.getContact(crmContactId)
                .switchIfEmpty(Mono.error(new ContactNotFoundException(crmContactId)))
                .map(inforContactMapper::toCanonical)
                .flatMap(contact -> {
                    var hubSpotRequest = hubSpotContactMapper.toHubSpot(contact);
                    Optional<String> mappedHubSpotId = contactIdentityService.findHubSpotContactId(contact.crmContactId());
                    Optional<String> existingHubSpotId = mappedHubSpotId
                            .or(() -> Optional.ofNullable(contact.hubSpotRecordId()));

                    return existingHubSpotId
                            .map(hubSpotId -> {
                                LOGGER.info("Existing HubSpot mapping found for {} -> {}; updating contact", contact.crmContactId(), hubSpotId);
                                Mono<Void> update = hubSpotClient.updateContact(hubSpotId, hubSpotRequest);
                                return update.then(Mono.defer(() -> {
                                    if (mappedHubSpotId.isEmpty()) {
                                        contactIdentityService.saveMapping(
                                                contact.crmContactId(),
                                                hubSpotId,
                                                contact.email()
                                        );
                                    }
                                    if (!hubSpotId.equals(contact.hubSpotRecordId())) {
                                        return inforCrmClient.updateHubSpotRecordId(
                                                contact.crmContactId(),
                                                hubSpotId
                                        );
                                    }
                                    return Mono.empty();
                                }));
                            })
                            .orElseGet(() -> {
                                LOGGER.info("No HubSpot mapping found for {}; creating a HubSpot contact", contact.crmContactId());
                                return hubSpotClient.createContact(hubSpotRequest)
                                        .flatMap(newHubSpotId -> {
                                            contactIdentityService.saveMapping(
                                                    contact.crmContactId(),
                                                    newHubSpotId,
                                                    contact.email()
                                            );

                                            return inforCrmClient.updateHubSpotRecordId(
                                                    contact.crmContactId(),
                                                    newHubSpotId
                                            );
                                        });
                            });
                });
    }

    public Mono<Void> syncHubSpotContactToInfor(String hubSpotContactId) {
        return hubSpotClient.getContact(hubSpotContactId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "HubSpot contact not found: " + hubSpotContactId
                )))
                .map(hubSpotContactMapper::fromHubSpot)
                .flatMap(contact -> {
                    String crmContactId = Optional.ofNullable(contact.crmContactId())
                            .filter(id -> !id.isBlank())
                            .orElseGet(() -> contactIdentityService.findCrmContactId(hubSpotContactId).orElse(null));

                    if (crmContactId == null || crmContactId.isBlank()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "HubSpot contact must include crm_contact_id or have a saved identity mapping: "
                                        + hubSpotContactId
                        ));
                    }

                    LOGGER.info("HubSpot contact {} found for CRM contact {}; syncing HubSpot data to INFOR", hubSpotContactId, crmContactId);

                    var inforContact = new com.modernice.integration.connector.infor.crm.dto.InforContactDto(
                            crmContactId,
                            contact.firstName(),
                            contact.lastName(),
                            contact.accountName(),
                            contact.miCustomerId(),
                            contact.crmAccountId(),
                            contact.city(),
                            contact.state(),
                            contact.workPhone(),
                            contact.mobilePhone(),
                            contact.email(),
                            contact.accountManager(),
                            contact.subType(),
                            contact.erpTerritory(),
                            contact.createdDate(),
                            contact.modifiedDate(),
                            contact.owner(),
                            contact.erpContactNumber(),
                            hubSpotContactId
                    );

                    return inforCrmClient.upsertContact(inforContact)
                            .then(Mono.fromRunnable(() -> {
                                LOGGER.info("HubSpot data synced to INFOR for CRM contact {} from HubSpot ID {}", crmContactId, hubSpotContactId);
                                contactIdentityService.saveMapping(
                                        crmContactId,
                                        hubSpotContactId,
                                        contact.email()
                                );
                            }))
                            .then(inforCrmClient.updateHubSpotRecordId(crmContactId, hubSpotContactId));
                });
    }
}
