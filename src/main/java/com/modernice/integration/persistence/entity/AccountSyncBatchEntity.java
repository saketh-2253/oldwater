package com.modernice.integration.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_sync_batch")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountSyncBatchEntity {
    @Id
    private UUID id;
    @Column(name = "total_count", nullable = false)
    private int totalCount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
