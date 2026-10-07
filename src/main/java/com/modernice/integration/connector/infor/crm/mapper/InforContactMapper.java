package com.modernice.integration.connector.infor.crm.mapper;

import com.modernice.integration.connector.infor.crm.dto.InforContactDto;
import com.modernice.integration.domain.model.Contact;
import org.springframework.stereotype.Component;

@Component
public class InforContactMapper {

    public Contact toCanonical(InforContactDto source) {
        return new Contact(
                source.crmContactId(),
                source.firstName(),
                source.lastName(),
                source.account(),
                source.miCustomerId(),
                source.crmAccountId(),
                source.city(),
                source.state(),
                source.workPhone(),
                source.mobile(),
                source.email(),
                source.acctManager(),
                source.subType(),
                source.erpTerritory(),
                source.createDate(),
                source.modifyDate(),
                source.owner(),
                source.erpContactNo(),
                source.hubSpotRecordId()
        );
    }
}
