package com.modernice.integration;

import com.modernice.integration.connector.hubspot.HubSpotClient;
import com.modernice.integration.connector.hubspot.MockHubSpotClient;
import com.modernice.integration.connector.hubspot.dto.HubSpotContactRequest;
import com.modernice.integration.connector.hubspot.mapper.HubSpotContactMapper;
import com.modernice.integration.connector.infor.crm.InforCrmClient;
import com.modernice.integration.connector.infor.crm.MockInforCrmClient;
import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import com.modernice.integration.connector.infor.crm.mapper.InforContactMapper;
import com.modernice.integration.controller.ContactSyncController;
import com.modernice.integration.domain.model.Contact;
import com.modernice.integration.identity.ContactIdentityService;
import com.modernice.integration.mapping.ValueMappingService;
import com.modernice.integration.persistence.entity.IntegrationIdentityEntity;
import com.modernice.integration.persistence.repository.IntegrationIdentityRepository;
import com.modernice.integration.workflow.ContactSyncWorkflow;
import com.modernice.integration.workflow.ContactSyncBatchService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationApplicationTests {

    @Mock
    private InforCrmClient inforCrmClient;

    @Mock
    private InforContactMapper inforContactMapper;

    @Mock
    private HubSpotClient hubSpotClient;

    @Mock
    private ContactIdentityService contactIdentityService;

        @Mock
        private ContactSyncWorkflow contactSyncWorkflow;

    @Mock
    private ContactSyncBatchService contactSyncBatchService;

    @Mock
    private IntegrationIdentityRepository integrationIdentityRepository;

        @Test
        void getHubSpotContact_returnsIdAndProperties() {
        when(hubSpotClient.getContact("hub-123")).thenReturn(Mono.just(Map.of(
            "firstname", "Alex",
            "email", "alex@example.com"
        )));
        var controller = new ContactSyncController(contactSyncWorkflow, contactSyncBatchService, inforCrmClient, hubSpotClient);

        var response = controller.getHubSpotContact("hub-123").block();

        assertThat(response)
            .containsEntry("id", "hub-123")
            .containsEntry("firstname", "Alex")
            .containsEntry("email", "alex@example.com");
        }

        @Test
        void updateHubSpotContact_mapsInforStyleParamsToHubSpotProperties() {
        when(hubSpotClient.getContact("hub-123")).thenReturn(Mono.just(Map.of(
            "firstname", "John",
            "lastname", "Smith"
        )));
        when(hubSpotClient.upsertContact(eq("hub-123"), any(HubSpotContactRequest.class)))
            .thenReturn(Mono.empty());
        var controller = new ContactSyncController(contactSyncWorkflow, contactSyncBatchService, inforCrmClient, hubSpotClient);

        when(contactSyncWorkflow.syncHubSpotContactToInfor("hub-123")).thenReturn(Mono.empty());
        controller.upsertHubSpotContact("hub-123", Map.of(
            "firstName", "Saketh",
            "lastName", "Reddy"
        )).block();

        ArgumentCaptor<HubSpotContactRequest> captor = ArgumentCaptor.forClass(HubSpotContactRequest.class);
        verify(hubSpotClient).upsertContact(eq("hub-123"), captor.capture());
        assertThat(captor.getValue().properties())
            .containsEntry("firstname", "Saketh")
            .containsEntry("lastname", "Reddy");
        verify(contactSyncWorkflow).syncHubSpotContactToInfor("hub-123");
        }

            @Test
            void upsertHubSpotContact_createsMissingRecordAndSyncsItToInfor() {
            when(hubSpotClient.getContact("hs-102")).thenReturn(Mono.empty());
            when(hubSpotClient.upsertContact(eq("hs-102"), any(HubSpotContactRequest.class)))
                .thenReturn(Mono.empty());
            when(contactSyncWorkflow.syncHubSpotContactToInfor("hs-102")).thenReturn(Mono.empty());
            var controller = new ContactSyncController(contactSyncWorkflow, contactSyncBatchService, inforCrmClient, hubSpotClient);

            controller.upsertHubSpotContact("hs-102", Map.of(
                "crmContactId", "crm-102",
                "firstName", "Micheal",
                "lastName", "Jordon"
            )).block();

            ArgumentCaptor<HubSpotContactRequest> captor = ArgumentCaptor.forClass(HubSpotContactRequest.class);
            verify(hubSpotClient).upsertContact(eq("hs-102"), captor.capture());
            assertThat(captor.getValue().properties())
                .containsEntry("crm_contact_id", "crm-102")
                .containsEntry("firstname", "Micheal")
                .containsEntry("lastname", "Jordon");
            verify(contactSyncWorkflow).syncHubSpotContactToInfor("hs-102");
            }

    @Test
    void mockInforCrmClient_canUpdateContactAtRuntime() {
        var client = new MockInforCrmClient();
        var missing = client.getContact("missing-contact").block();
        assertThat(missing).isNull();

        var contact = new InforContactDto(
                "103",
                "Saketh",
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
                "hub-999"
        );

        client.setContact(contact).block();
        var updated = client.getContact("103").block();
        assertThat(updated).isNotNull();
        assertThat(updated.firstName()).isEqualTo("Saketh");

        client.setContact(new InforContactDto(
                "103",
                "Ksaketh",
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
                "hub-999"
        )).block();

        var changed = client.getContact("103").block();
        assertThat(changed).isNotNull();
        assertThat(changed.firstName()).isEqualTo("Ksaketh");
    }

    @Test
    void saveMapping_updatesExistingIdentityInsteadOfCreatingDuplicate() {
        var service = new ContactIdentityService(integrationIdentityRepository);
        var existing = IntegrationIdentityEntity.builder()
                .id(42L)
                .entityType("CONTACT")
                .sourceSystem("INFOR_CRM")
                .sourceId("crm-123")
                .targetSystem("HUBSPOT")
                .targetId("hub-987")
                .businessKey("old@example.com")
                .lastSyncedAt(Instant.parse("2026-05-06T14:30:00Z"))
                .build();

        when(integrationIdentityRepository.findByEntityTypeAndSourceSystemAndSourceId("CONTACT", "INFOR_CRM", "crm-123"))
                .thenReturn(Optional.of(existing));

        service.saveMapping("crm-123", "hub-987", "john.smith@example.com");

        verify(integrationIdentityRepository).save(existing);
    }

    @Test
    void syncInforContactToHubSpot_createsHubSpotRecordWhenNoExistingMapping() {
        var mapper = new HubSpotContactMapper(new ValueMappingService());
        var workflow = new ContactSyncWorkflow(inforCrmClient, inforContactMapper, mapper, hubSpotClient, contactIdentityService);

        var input = new InforContactDto(
                "crm-123",
                "John",
                "Smith",
                "Modern Ice Customer",
                "MI-100",
                "A-500",
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
        );

        var canonical = new Contact(
                "crm-123",
                "John",
                "Smith",
                "Modern Ice Customer",
                "MI-100",
                "A-500",
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
        );

        when(inforCrmClient.getContact("crm-123")).thenReturn(Mono.just(input));
        when(inforContactMapper.toCanonical(input)).thenReturn(canonical);
        when(contactIdentityService.findHubSpotContactId("crm-123")).thenReturn(Optional.empty());
        when(hubSpotClient.createContact(any(HubSpotContactRequest.class))).thenReturn(Mono.just("hub-987"));
        when(inforCrmClient.updateHubSpotRecordId("crm-123", "hub-987")).thenReturn(Mono.empty());

        workflow.syncInforContactToHubSpot("crm-123").block();

        ArgumentCaptor<HubSpotContactRequest> captor = ArgumentCaptor.forClass(HubSpotContactRequest.class);
        verify(hubSpotClient).createContact(captor.capture());
        verify(contactIdentityService).saveMapping("crm-123", "hub-987", "john.smith@example.com");
        verify(inforCrmClient).updateHubSpotRecordId("crm-123", "hub-987");
        assertThat(captor.getValue().properties()).containsEntry("crm_contact_id", "crm-123");
        assertThat(captor.getValue().properties()).containsEntry("email", "john.smith@example.com");
    }

    @Test
    void syncInforContactToHubSpot_updatesHubSpotRecordWhenExistingMappingExists() {
        var mapper = new HubSpotContactMapper(new ValueMappingService());
        var workflow = new ContactSyncWorkflow(inforCrmClient, inforContactMapper, mapper, hubSpotClient, contactIdentityService);
        var hubSpotContactId = "hubspot-contact-999";

        var input = new InforContactDto(
                "crm-456",
                "Jane",
                "Doe",
                "Another Account",
                "MI-200",
                "B-600",
                "Boston",
                "MA",
                "6175551000",
                "6175552000",
                "jane.doe@example.com",
                "U738UA00000M",
                "Customer",
                "200",
                Instant.parse("2026-05-06T14:30:00Z"),
                Instant.now(),
                "OWNER-1",
                "7654",
                null
        );

        var canonical = new Contact(
                "crm-456",
                "Jane",
                "Doe",
                "Another Account",
                "MI-200",
                "B-600",
                "Boston",
                "MA",
                "6175551000",
                "6175552000",
                "jane.doe@example.com",
                "U738UA00000M",
                "Customer",
                "200",
                Instant.parse("2026-05-06T14:30:00Z"),
                Instant.now(),
                "OWNER-1",
                "7654",
                null
        );

        when(inforCrmClient.getContact("crm-456")).thenReturn(Mono.just(input));
        when(inforContactMapper.toCanonical(input)).thenReturn(canonical);
        when(contactIdentityService.findHubSpotContactId("crm-456")).thenReturn(Optional.of(hubSpotContactId));
        when(hubSpotClient.updateContact(eq(hubSpotContactId), any(HubSpotContactRequest.class))).thenReturn(Mono.empty());
        when(inforCrmClient.updateHubSpotRecordId("crm-456", hubSpotContactId)).thenReturn(Mono.empty());

        workflow.syncInforContactToHubSpot("crm-456").block();

        verify(hubSpotClient).updateContact(eq(hubSpotContactId), any(HubSpotContactRequest.class));
        verify(hubSpotClient, never()).createContact(any(HubSpotContactRequest.class));
        verify(contactIdentityService, never()).saveMapping(anyString(), anyString(), anyString());
        verify(inforCrmClient).updateHubSpotRecordId("crm-456", hubSpotContactId);
    }

    @Test
    void syncInforContactToHubSpot_usesInforHubSpotIdWhenIdentityMappingIsMissing() {
        var mapper = new HubSpotContactMapper(new ValueMappingService());
        var workflow = new ContactSyncWorkflow(inforCrmClient, inforContactMapper, mapper, hubSpotClient, contactIdentityService);
        var input = new InforContactDto(
                "crm-789", "Sam", "Taylor", null, null, null, "Denver", "CO",
                null, null, "sam.taylor@example.com", null, null, null, null, null, null, null, "hub-789"
        );
        var canonical = new Contact(
                "crm-789", "Sam", "Taylor", null, null, null, "Denver", "CO",
                null, null, "sam.taylor@example.com", null, null, null, null, null, null, null, "hub-789"
        );

        when(inforCrmClient.getContact("crm-789")).thenReturn(Mono.just(input));
        when(inforContactMapper.toCanonical(input)).thenReturn(canonical);
        when(contactIdentityService.findHubSpotContactId("crm-789")).thenReturn(Optional.empty());
        when(hubSpotClient.updateContact(eq("hub-789"), any(HubSpotContactRequest.class))).thenReturn(Mono.empty());

        workflow.syncInforContactToHubSpot("crm-789").block();

        verify(hubSpotClient).updateContact(eq("hub-789"), any(HubSpotContactRequest.class));
        verify(hubSpotClient, never()).createContact(any(HubSpotContactRequest.class));
        verify(contactIdentityService).saveMapping("crm-789", "hub-789", "sam.taylor@example.com");
        verify(inforCrmClient, never()).updateHubSpotRecordId(anyString(), anyString());
    }

    @Test
    void syncHubSpotContactToInfor_createsOrUpdatesInforRecordUsingMappedCrmId() {
        var mapper = new HubSpotContactMapper(new ValueMappingService());
        var workflow = new ContactSyncWorkflow(inforCrmClient, inforContactMapper, mapper, hubSpotClient, contactIdentityService);

        when(hubSpotClient.getContact("hub-321")).thenReturn(Mono.just(Map.of(
                "firstname", "Alice",
                "lastname", "Brown",
                "company", "Northwind",
                "email", "alice.brown@example.com",
                "city", "Seattle",
                "state", "WA",
                "account_manager", "Manager A",
                "erp_territory", "100",
                "erp_contact_no", "654321"
        )));
        when(contactIdentityService.findCrmContactId("hub-321")).thenReturn(Optional.of("crm-321"));
        when(inforCrmClient.upsertContact(any(InforContactDto.class))).thenReturn(Mono.empty());
        when(inforCrmClient.updateHubSpotRecordId("crm-321", "hub-321")).thenReturn(Mono.empty());

        workflow.syncHubSpotContactToInfor("hub-321").block();

        verify(inforCrmClient).upsertContact(any(InforContactDto.class));
        verify(contactIdentityService).saveMapping("crm-321", "hub-321", "alice.brown@example.com");
        verify(inforCrmClient).updateHubSpotRecordId("crm-321", "hub-321");
    }

        @Test
        void syncInforContactToHubSpot_doesNotSaveFallbackMappingWhenHubSpotUpdateFails() {
        var mapper = new HubSpotContactMapper(new ValueMappingService());
        var workflow = new ContactSyncWorkflow(inforCrmClient, inforContactMapper, mapper, hubSpotClient, contactIdentityService);
        var input = new InforContactDto(
            "crm-failure", "Sam", "Taylor", null, null, null, "Denver", "CO",
            null, null, "sam.taylor@example.com", null, null, null, null, null, null, null, "hubspot-789"
        );
        var canonical = new Contact(
            "crm-failure", "Sam", "Taylor", null, null, null, "Denver", "CO",
            null, null, "sam.taylor@example.com", null, null, null, null, null, null, null, "hubspot-789"
        );
        var failure = new IllegalStateException("HubSpot update failed");

        when(inforCrmClient.getContact("crm-failure")).thenReturn(Mono.just(input));
        when(inforContactMapper.toCanonical(input)).thenReturn(canonical);
        when(contactIdentityService.findHubSpotContactId("crm-failure")).thenReturn(Optional.empty());
        when(hubSpotClient.updateContact(eq("hubspot-789"), any(HubSpotContactRequest.class)))
            .thenReturn(Mono.error(failure));

        assertThatThrownBy(() -> workflow.syncInforContactToHubSpot("crm-failure").block())
            .isSameAs(failure);

        verify(contactIdentityService, never()).saveMapping(anyString(), anyString(), anyString());
        verify(hubSpotClient, never()).createContact(any(HubSpotContactRequest.class));
        }

    @Test
        void mockHubSpotClient_updatesOnlyChangedPropertiesAndSkipsUnchangedValues() {
        var client = new MockHubSpotClient();
        var firstRequest = new HubSpotContactRequest(Map.of(
                "firstname", "John",
                "city", "Cincinnati",
                "email", "john.smith@example.com"
        ));
        var logger = (Logger) LoggerFactory.getLogger(MockHubSpotClient.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);

        try {
            var hubSpotId = client.createContact(firstRequest).block();
            appender.list.clear();
            var changedRequest = new HubSpotContactRequest(Map.of(
                "firstname", "John",
                "city", "Boston",
                "email", "john.smith@example.com"
            ));

            client.updateContact(hubSpotId, changedRequest).block();

            assertThat(hubSpotId).isNotBlank();
            assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                    .contains("updated contact " + hubSpotId)
                    .contains("city=Boston"))
                .noneSatisfy(message -> assertThat(message).contains("firstname=John"));

                appender.list.clear();
                client.updateContact(hubSpotId, changedRequest).block();

                assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly("HubSpot mock contact " + hubSpotId + " is already up to date; no update needed");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
