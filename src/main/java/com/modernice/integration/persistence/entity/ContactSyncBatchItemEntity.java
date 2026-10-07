package com.modernice.integration.persistence.entity;

import com.modernice.integration.domain.enums.ContactSyncItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contact_sync_batch_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactSyncBatchItemEntity {

    @Id
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "crm_contact_id", nullable = false, length = 255)
    private String crmContactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactSyncItemStatus status;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
