package com.modernice.integration;

import com.modernice.integration.connector.hubspot.HubSpotClient;
import com.modernice.integration.connector.hubspot.dto.HubSpotCompanyRequest;
import com.modernice.integration.connector.hubspot.mapper.HubSpotCompanyMapper;
import com.modernice.integration.connector.infor.crm.InforCrmClient;
import com.modernice.integration.connector.infor.crm.MockInforCrmClient;
import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import com.modernice.integration.connector.infor.crm.mapper.InforAccountMapper;
import com.modernice.integration.identity.CompanyIdentityService;
import com.modernice.integration.workflow.AccountSyncWorkflow;
import com.modernice.integration.mapping.ValueMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSyncTests {

    @Mock
    private InforCrmClient inforCrmClient;
    @Mock
    private HubSpotClient hubSpotClient;
    @Mock
    private CompanyIdentityService companyIdentityService;

    @Test
    void localMockAccountUpdatePreservesHubSpotIdWhenRequestContainsNull() {
        var client = new MockInforCrmClient();
        client.initializeDefaultContacts();
        client.updateHubSpotCompanyId("A500", "50000001").block();

        client.upsertAccount(new InforAccountDto(
                "12345", "A500", "Modern Ice Updated", "123 Main Street", null,
                "Cincy", "OH", "45202", "5135551000", null, "Customer",
                "Active", "100", "U738UA00000M", "DI", "U738UA00002W", null
        )).block();

        InforAccountDto updated = client.getAccount("A500").block();
        assertThat(updated).isNotNull();
        assertThat(updated.city()).isEqualTo("Cincy");
        assertThat(updated.hubSpotCompanyId()).isEqualTo("50000001");
    }

    @Test
    void syncInforAccountToHubSpotCreatesCompanyAndStoresMappings() {
        InforAccountDto account = new InforAccountDto(
                "CUS-123", "ACC-123", "Modern Ice", "1 Ice Way", "Suite 2",
                "Cincinnati", "OH", "45202", "5135550100", null, "Customer",
                "Active", "100", "Manager-1", "DI", "Owner-1", null
        );
        when(inforCrmClient.getAccount("ACC-123")).thenReturn(Mono.just(account));
        when(companyIdentityService.findHubSpotCompanyId("ACC-123")).thenReturn(Optional.empty());
        when(hubSpotClient.createCompany(any(HubSpotCompanyRequest.class))).thenReturn(Mono.just("hs-company-1"));
        when(inforCrmClient.updateHubSpotCompanyId("ACC-123", "hs-company-1")).thenReturn(Mono.empty());

        workflow().syncInforAccountToHubSpot("ACC-123").block();

        ArgumentCaptor<HubSpotCompanyRequest> request = ArgumentCaptor.forClass(HubSpotCompanyRequest.class);
        verify(hubSpotClient).createCompany(request.capture());
        assertThat(request.getValue().properties())
                .containsEntry("customer_id", "CUS-123")
                .containsEntry("name", "Modern Ice")
                .containsEntry("address", "1 Ice Way")
                .containsEntry("address2", "Suite 2")
                .containsEntry("zip", "45202")
                .containsEntry("erp_territory", "1-NORTHEAST REGION")
                .containsEntry("customer_type", "Competitive Distributor")
                .containsEntry("infor_account_id", "ACC-123");
        verify(companyIdentityService).saveMapping("ACC-123", "hs-company-1", "CUS-123");
        verify(inforCrmClient).updateHubSpotCompanyId("ACC-123", "hs-company-1");
    }

    @Test
    void syncHubSpotCompanyToInforUsesMappedAccountIdAndPersistsCompany() {
        when(hubSpotClient.getCompany("hs-company-2")).thenReturn(Mono.just(Map.of(
                "customer_id", "CUS-2",
                "infor_account_id", "ACC-2",
                "name", "Northwind",
                "address", "2 Main Street",
                "city", "Boston",
                "state", "MA",
                "zip", "02110",
                "phone", "6175550100",
                "customer_type", "Competitive Distributor",
                "erp_territory", "1-NORTHEAST REGION"
        )));
        when(inforCrmClient.upsertAccount(any(InforAccountDto.class))).thenReturn(Mono.empty());
        when(inforCrmClient.updateHubSpotCompanyId("ACC-2", "hs-company-2")).thenReturn(Mono.empty());

        workflow().syncHubSpotCompanyToInfor("hs-company-2").block();

        ArgumentCaptor<InforAccountDto> account = ArgumentCaptor.forClass(InforAccountDto.class);
        verify(inforCrmClient).upsertAccount(account.capture());
        assertThat(account.getValue().accountId()).isEqualTo("ACC-2");
        assertThat(account.getValue().customerId()).isEqualTo("CUS-2");
        assertThat(account.getValue().name()).isEqualTo("Northwind");
        assertThat(account.getValue().hubSpotCompanyId()).isEqualTo("hs-company-2");
        verify(companyIdentityService).saveMapping("ACC-2", "hs-company-2", "CUS-2");
    }

    private AccountSyncWorkflow workflow() {
        return new AccountSyncWorkflow(
                inforCrmClient,
                new InforAccountMapper(),
                new HubSpotCompanyMapper(new ValueMappingService()),
                hubSpotClient,
                companyIdentityService
        );
    }
}
