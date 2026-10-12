package com.modernice.integration.persistence.entity;

import com.modernice.integration.domain.enums.ContactSyncItemStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_sync_batch_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountSyncBatchItemEntity {
    @Id
    private UUID id;
    @Column(name = "batch_id", nullable = false)
    private UUID batchId;
    @Column(name = "account_id", nullable = false, length = 255)
    private String accountId;
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
