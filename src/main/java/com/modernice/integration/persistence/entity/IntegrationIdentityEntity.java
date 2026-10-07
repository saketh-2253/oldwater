package com.modernice.integration.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "integration_identity",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_identity_source",
               columnNames = {"entity_type", "source_system", "source_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationIdentityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(name = "target_system", nullable = false)
    private String targetSystem;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(name = "business_key")
    private String businessKey;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
}
