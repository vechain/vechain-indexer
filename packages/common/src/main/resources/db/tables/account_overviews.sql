------------------------------------------------------------
-- ACCOUNT OVERVIEW TABLE (versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS account_overviews (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    first_seen BIGINT NOT NULL,
    last_seen BIGINT NOT NULL,
    transactions_sent BIGINT NOT NULL,
    clauses_sent BIGINT NOT NULL,
    vtho_burned NUMERIC NOT NULL,
    vtho_delegated NUMERIC NOT NULL,
    gas_used NUMERIC NOT NULL,
    vet_sent NUMERIC NOT NULL,
    vet_received NUMERIC NOT NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial index for current records (optimizes WHERE is_current = true queries)
CREATE INDEX IF NOT EXISTS idx_account_overviews_current
    ON account_overviews (entity_id) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_account_overviews_block_number
    ON account_overviews (block_number);
