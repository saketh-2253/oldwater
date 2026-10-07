package com.modernice.integration.persistence.repository;

import com.modernice.integration.persistence.entity.IntegrationIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationIdentityRepository extends JpaRepository<IntegrationIdentityEntity, Long> {

    Optional<IntegrationIdentityEntity> findByEntityTypeAndSourceSystemAndSourceId(
            String entityType,
            String sourceSystem,
            String sourceId
    );

    Optional<IntegrationIdentityEntity> findByEntityTypeAndTargetSystemAndTargetId(
            String entityType,
            String targetSystem,
            String targetId
    );
}
