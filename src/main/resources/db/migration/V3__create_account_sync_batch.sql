CREATE TABLE account_sync_batch (
    id UUID PRIMARY KEY,
    total_count INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE account_sync_batch_item (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES account_sync_batch(id),
    account_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_account_sync_batch_account UNIQUE (batch_id, account_id)
);

CREATE INDEX idx_account_sync_batch_item_status ON account_sync_batch_item (batch_id, status);
