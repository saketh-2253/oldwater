package com.modernice.integration.workflow;

import com.modernice.integration.connector.hubspot.HubSpotClient;
import com.modernice.integration.connector.hubspot.mapper.HubSpotCompanyMapper;
import com.modernice.integration.connector.infor.crm.InforCrmClient;
import com.modernice.integration.connector.infor.crm.dto.InforAccountDto;
import com.modernice.integration.connector.infor.crm.mapper.InforAccountMapper;
import com.modernice.integration.identity.CompanyIdentityService;
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
public class AccountSyncWorkflow {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountSyncWorkflow.class);

    private final InforCrmClient inforCrmClient;
    private final InforAccountMapper inforAccountMapper;
    private final HubSpotCompanyMapper hubSpotCompanyMapper;
    private final HubSpotClient hubSpotClient;
    private final CompanyIdentityService companyIdentityService;

    public Mono<Void> syncInforAccountToHubSpot(String accountId) {
        return inforCrmClient.getAccount(accountId)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(accountId)))
                .map(inforAccountMapper::toCanonical)
                .flatMap(company -> {
                    var request = hubSpotCompanyMapper.toHubSpot(company);
                    Optional<String> mappedCompanyId =
                            companyIdentityService.findHubSpotCompanyId(company.accountId());
                    Optional<String> existingCompanyId = mappedCompanyId
                            .or(() -> Optional.ofNullable(company.hubSpotCompanyId()));

                    return existingCompanyId
                            .map(hubSpotCompanyId -> {
                                LOGGER.info("Existing HubSpot company mapping found for {} -> {}; updating company",
                                        company.accountId(), hubSpotCompanyId);
                                return hubSpotClient.updateCompany(hubSpotCompanyId, request)
                                        .then(Mono.defer(() -> {
                                            if (mappedCompanyId.isEmpty()) {
                                                companyIdentityService.saveMapping(
                                                        company.accountId(),
                                                        hubSpotCompanyId,
                                                        company.customerId()
                                                );
                                            }
                                            if (!hubSpotCompanyId.equals(company.hubSpotCompanyId())) {
                                                return inforCrmClient.updateHubSpotCompanyId(
                                                        company.accountId(),
                                                        hubSpotCompanyId
                                                );
                                            }
                                            return Mono.empty();
                                        }));
                            })
                            .orElseGet(() -> {
                                LOGGER.info("No HubSpot company mapping found for {}; creating company",
                                        company.accountId());
                                return hubSpotClient.createCompany(request)
                                        .flatMap(hubSpotCompanyId -> {
                                            companyIdentityService.saveMapping(
                                                    company.accountId(),
                                                    hubSpotCompanyId,
                                                    company.customerId()
                                            );
                                            return inforCrmClient.updateHubSpotCompanyId(
                                                    company.accountId(),
                                                    hubSpotCompanyId
                                            );
                                        });
                            });
                });
    }

    public Mono<Void> syncHubSpotCompanyToInfor(String hubSpotCompanyId) {
        return hubSpotClient.getCompany(hubSpotCompanyId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "HubSpot company not found: " + hubSpotCompanyId
                )))
                .flatMap(properties -> {
                    var company = hubSpotCompanyMapper.fromHubSpot(properties, hubSpotCompanyId);
                    String accountId = Optional.ofNullable(company.accountId())
                            .filter(id -> !id.isBlank())
                            .or(() -> companyIdentityService.findInforAccountId(hubSpotCompanyId))
                            .orElseThrow(() -> new ResponseStatusException(
                                    HttpStatus.BAD_REQUEST,
                                    "HubSpot company must include infor_account_id or have a saved identity mapping"
                            ));
                    InforAccountDto account = new InforAccountDto(
                            company.customerId(),
                            accountId,
                            company.name(),
                            company.address1(),
                            company.address2(),
                            company.city(),
                            company.state(),
                            company.postalCode(),
                            company.mainPhone(),
                            company.type(),
                            company.subType(),
                            company.status(),
                            company.erpTerritory(),
                            company.accountManager(),
                            company.customerType(),
                            company.owner(),
                            hubSpotCompanyId
                    );
                    return inforCrmClient.upsertAccount(account)
                            .then(Mono.fromRunnable(() -> companyIdentityService.saveMapping(
                                    accountId,
                                    hubSpotCompanyId,
                                    company.customerId()
                            )))
                            .then(inforCrmClient.updateHubSpotCompanyId(accountId, hubSpotCompanyId));
                });
    }
}
