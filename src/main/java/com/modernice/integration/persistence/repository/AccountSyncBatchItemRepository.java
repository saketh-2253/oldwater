package com.modernice.integration.persistence.repository;

import com.modernice.integration.domain.enums.ContactSyncItemStatus;
import com.modernice.integration.persistence.entity.AccountSyncBatchItemEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountSyncBatchItemRepository extends JpaRepository<AccountSyncBatchItemEntity, UUID> {
    long countByBatchIdAndStatus(UUID batchId, ContactSyncItemStatus status);
    Page<AccountSyncBatchItemEntity> findByBatchId(UUID batchId, Pageable pageable);
}
