package com.modernice.integration.persistence.repository;

import com.modernice.integration.persistence.entity.AccountSyncBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountSyncBatchRepository extends JpaRepository<AccountSyncBatchEntity, UUID> {}
