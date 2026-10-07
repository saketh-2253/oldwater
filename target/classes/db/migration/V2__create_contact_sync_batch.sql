CREATE TABLE contact_sync_batch (
    id UUID PRIMARY KEY,
    total_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE contact_sync_batch_item (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES contact_sync_batch(id),
    crm_contact_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_contact_sync_batch_contact UNIQUE (batch_id, crm_contact_id)
);

CREATE INDEX idx_contact_sync_batch_item_status ON contact_sync_batch_item (batch_id, status);
