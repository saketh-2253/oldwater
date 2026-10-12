package com.modernice.integration;

import com.modernice.integration.connector.infor.crm.InforSDataMapper;
import com.modernice.integration.connector.infor.crm.InforSDataProperties;
import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InforSDataMapperTests {

    private final InforSDataMapper mapper = new InforSDataMapper(new InforSDataProperties());

    @Test
    void mapsContactFieldsAndSDataReferences() {
        InforContactDto contact = new InforContactDto(
                "C-123", "Sam", "Smith", "Modern Ice", "12345", "A-500",
                "Cincinnati", "OH", "5135551000", "5135552000", "sam@example.com",
                "U-1", "Customer", "100", Instant.parse("2026-05-06T14:30:00Z"),
                Instant.parse("2026-10-05T15:49:20Z"), "U-2", "4321", "HS-1"
        );

        Map<String, Object> sdata = mapper.toSDataContact(contact);

        assertThat(sdata)
                .containsEntry("FirstName", "Sam")
                .containsEntry("LastName", "Smith")
                .containsEntry("MiCustomerId", "12345")
                .containsEntry("ErpTerritory", "100")
                .containsEntry("ErpExtId", "4321")
                .containsEntry("HubSpotRecordId", "HS-1");
        assertThat(sdata.get("Account")).isEqualTo(Map.of("$key", "A-500"));
        assertThat(sdata.get("Address")).isEqualTo(Map.of("City", "Cincinnati", "State", "OH"));
    }

    @Test
    void mapsSDataAccountFieldsAndAddress() {
        InforAccountDto account = new InforAccountDto(
                "12345", "A-500", "Modern Ice", "1 Main Street", "Suite 2",
                "Cincinnati", "OH", "45202", "5135551000", null, "Customer",
                "Active", "100", "U-1", "DI", "U-2", "HS-2"
        );

        Map<String, Object> sdata = mapper.toSDataAccount(account);

        assertThat(sdata)
                .containsEntry("ExternalAccountNumber", "12345")
                .containsEntry("AccountName", "Modern Ice")
                .containsEntry("ErpTerritory", "100")
                .containsEntry("ErpCustType", "DI")
                .containsEntry("HubSpotCompanyId", "HS-2");
        assertThat(sdata.get("Address")).isEqualTo(Map.of(
                "Address1", "1 Main Street",
                "Address2", "Suite 2",
                "City", "Cincinnati",
                "State", "OH",
                "PostalCode", "45202"
        ));
    }

    @Test
    void mapsContactResponseAndSDataDateValues() {
        InforContactDto contact = mapper.fromSDataContact(
                "C-123",
                Map.of(
                        "$key", "C-123",
                        "FirstName", "Sam",
                        "LastName", "Smith",
                        "AccountName", "Modern Ice",
                        "Account", Map.of("$key", "A-500"),
                        "CreateDate", "/Date(1778077800000)/",
                        "Email", "sam@example.com"
                ),
                Map.of("City", "Cincinnati", "State", "OH"),
                Map.of("$key", "A-500", "ExternalAccountNumber", "12345")
        );

        assertThat(contact.crmContactId()).isEqualTo("C-123");
        assertThat(contact.crmAccountId()).isEqualTo("A-500");
        assertThat(contact.miCustomerId()).isEqualTo("12345");
        assertThat(contact.city()).isEqualTo("Cincinnati");
        assertThat(contact.state()).isEqualTo("OH");
    }
}
