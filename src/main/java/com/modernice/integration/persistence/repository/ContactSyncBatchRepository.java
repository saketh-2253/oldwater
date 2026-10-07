package com.modernice.integration.persistence.repository;

import com.modernice.integration.persistence.entity.ContactSyncBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContactSyncBatchRepository extends JpaRepository<ContactSyncBatchEntity, UUID> {}
