package com.modernice.integration.connector.infor.crm;

import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import reactor.core.publisher.Mono;

public interface InforCrmClient {
    Mono<InforContactDto> getContact(String crmContactId);
    Mono<Void> setContact(InforContactDto contact);
    Mono<Void> upsertContact(InforContactDto contact);
    Mono<Void> updateHubSpotRecordId(String crmContactId, String hubSpotRecordId);
    Mono<InforAccountDto> getAccount(String accountId);
    Mono<Void> upsertAccount(InforAccountDto account);
    Mono<Void> updateHubSpotCompanyId(String accountId, String hubSpotCompanyId);
}
