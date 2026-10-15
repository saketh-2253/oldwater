package com.modernice.integration.identity;

import com.modernice.integration.persistence.entity.IntegrationIdentityEntity;
import com.modernice.integration.persistence.repository.IntegrationIdentityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BusinessIdentityService {
    private final IntegrationIdentityRepository repository;

    public Optional<String> findTargetId(String entityType, String sourceSystem, String sourceId) {
        return repository.findByEntityTypeAndSourceSystemAndSourceId(entityType, sourceSystem, sourceId)
                .map(IntegrationIdentityEntity::getTargetId);
    }

    public Optional<String> findSourceId(String entityType, String targetSystem, String targetId) {
        return repository.findByEntityTypeAndTargetSystemAndTargetId(entityType, targetSystem, targetId)
                .map(IntegrationIdentityEntity::getSourceId);
    }

    public void save(String entityType, String sourceSystem, String sourceId,
                     String targetSystem, String targetId, String businessKey) {
        repository.findByEntityTypeAndSourceSystemAndSourceId(entityType, sourceSystem, sourceId)
                .ifPresentOrElse(existing -> {
                    existing.setTargetSystem(targetSystem);
                    existing.setTargetId(targetId);
                    existing.setBusinessKey(businessKey);
                    existing.setLastSyncedAt(Instant.now());
                    repository.save(existing);
                }, () -> repository.save(IntegrationIdentityEntity.builder()
                        .entityType(entityType)
                        .sourceSystem(sourceSystem)
                        .sourceId(sourceId)
                        .targetSystem(targetSystem)
                        .targetId(targetId)
                        .businessKey(businessKey)
                        .lastSyncedAt(Instant.now())
                        .build()));
    }
}
