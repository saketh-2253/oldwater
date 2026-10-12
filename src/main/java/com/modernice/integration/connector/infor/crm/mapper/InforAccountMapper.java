package com.modernice.integration.connector.infor.crm.mapper;

import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import com.modernice.integration.domain.model.Company;
import org.springframework.stereotype.Component;

@Component
public class InforAccountMapper {

    public Company toCanonical(InforAccountDto source) {
        return new Company(
                source.customerId(),
                source.accountId(),
                source.name(),
                source.address1(),
                source.address2(),
                source.city(),
                source.state(),
                source.postalCode(),
                source.mainPhone(),
                source.type(),
                source.subType(),
                source.status(),
                source.erpTerritory(),
                source.accountManager(),
                source.customerType(),
                source.owner(),
                source.hubSpotCompanyId()
        );
    }
}
