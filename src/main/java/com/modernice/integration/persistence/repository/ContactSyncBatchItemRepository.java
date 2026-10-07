package com.modernice.integration.persistence.repository;

import com.modernice.integration.domain.enums.ContactSyncItemStatus;
import com.modernice.integration.persistence.entity.ContactSyncBatchItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ContactSyncBatchItemRepository extends JpaRepository<ContactSyncBatchItemEntity, UUID> {
    long countByBatchIdAndStatus(UUID batchId, ContactSyncItemStatus status);
    Page<ContactSyncBatchItemEntity> findByBatchId(UUID batchId, Pageable pageable);
}
