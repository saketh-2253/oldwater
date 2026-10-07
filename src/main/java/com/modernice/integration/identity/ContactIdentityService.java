package com.modernice.integration.identity;

import com.modernice.integration.persistence.entity.IntegrationIdentityEntity;
import com.modernice.integration.persistence.repository.IntegrationIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContactIdentityService {

    private final IntegrationIdentityRepository repository;

    public Optional<String> findHubSpotContactId(String crmContactId) {
        return repository
                .findByEntityTypeAndSourceSystemAndSourceId(
                        "CONTACT",
                        "INFOR_CRM",
                        crmContactId
                )
                .map(IntegrationIdentityEntity::getTargetId);
    }

    public Optional<String> findCrmContactId(String hubSpotId) {
        return repository
                .findByEntityTypeAndTargetSystemAndTargetId(
                        "CONTACT",
                        "HUBSPOT",
                        hubSpotId
                )
                .map(IntegrationIdentityEntity::getSourceId);
    }

    public void saveMapping(String crmContactId, String hubSpotId, String email) {
        repository.findByEntityTypeAndSourceSystemAndSourceId("CONTACT", "INFOR_CRM", crmContactId)
                .ifPresentOrElse(existing -> {
                    existing.setTargetSystem("HUBSPOT");
                    existing.setTargetId(hubSpotId);
                    existing.setBusinessKey(email);
                    existing.setLastSyncedAt(Instant.now());
                    repository.save(existing);
                }, () -> repository.save(IntegrationIdentityEntity.builder()
                        .entityType("CONTACT")
                        .sourceSystem("INFOR_CRM")
                        .sourceId(crmContactId)
                        .targetSystem("HUBSPOT")
                        .targetId(hubSpotId)
                        .businessKey(email)
                        .lastSyncedAt(Instant.now())
                        .build()));
    }
}
