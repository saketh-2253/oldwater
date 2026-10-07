package com.modernice.integration.connector.infor.crm;

import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import reactor.core.publisher.Mono;

public interface InforCrmClient {
    Mono<InforContactDto> getContact(String crmContactId);
    Mono<Void> setContact(InforContactDto contact);
    Mono<Void> upsertContact(InforContactDto contact);
    Mono<Void> updateHubSpotRecordId(String crmContactId, String hubSpotRecordId);
}
