package com.modernice.integration.identity;

import com.modernice.integration.persistence.entity.IntegrationIdentityEntity;
import com.modernice.integration.persistence.repository.IntegrationIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompanyIdentityService {

    private final IntegrationIdentityRepository repository;

    public Optional<String> findHubSpotCompanyId(String inforAccountId) {
        return repository.findByEntityTypeAndSourceSystemAndSourceId(
                        "COMPANY",
                        "INFOR_CRM",
                        inforAccountId
                )
                .map(IntegrationIdentityEntity::getTargetId);
    }

    public Optional<String> findInforAccountId(String hubSpotCompanyId) {
        return repository.findByEntityTypeAndTargetSystemAndTargetId(
                        "COMPANY",
                        "HUBSPOT",
                        hubSpotCompanyId
                )
                .map(IntegrationIdentityEntity::getSourceId);
    }

    public void saveMapping(String inforAccountId, String hubSpotCompanyId, String customerId) {
        repository.findByEntityTypeAndSourceSystemAndSourceId("COMPANY", "INFOR_CRM", inforAccountId)
                .ifPresentOrElse(existing -> {
                    existing.setTargetSystem("HUBSPOT");
                    existing.setTargetId(hubSpotCompanyId);
                    existing.setBusinessKey(customerId);
                    existing.setLastSyncedAt(Instant.now());
                    repository.save(existing);
                }, () -> repository.save(IntegrationIdentityEntity.builder()
                        .entityType("COMPANY")
                        .sourceSystem("INFOR_CRM")
                        .sourceId(inforAccountId)
                        .targetSystem("HUBSPOT")
                        .targetId(hubSpotCompanyId)
                        .businessKey(customerId)
                        .lastSyncedAt(Instant.now())
                        .build()));
    }
}
