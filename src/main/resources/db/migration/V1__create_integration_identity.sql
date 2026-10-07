CREATE TABLE integration_identity (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    source_system VARCHAR(50) NOT NULL,
    source_id VARCHAR(255) NOT NULL,
    target_system VARCHAR(50) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    business_key VARCHAR(255),
    last_synced_at TIMESTAMPTZ
);

ALTER TABLE integration_identity
ADD CONSTRAINT uk_identity_source
UNIQUE (entity_type, source_system, source_id);
