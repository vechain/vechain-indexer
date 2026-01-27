------------------------------------------------------------
-- TRANSFER_EVENTS TABLE (non-versioned, append-only)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS transfer_events (
    id TEXT PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    tx_id TEXT NOT NULL,
    from_address TEXT NOT NULL,
    to_address TEXT NOT NULL,
    value TEXT NOT NULL,
    token_address TEXT,
    token_id TEXT,
    topics JSONB NOT NULL,
    event_type TEXT NOT NULL
);

-- Index for rollback operations
CREATE INDEX IF NOT EXISTS idx_transfer_events_block_number
    ON transfer_events (block_number);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_transfer_events_to_block
    ON transfer_events (to_address, block_number DESC, tx_id DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transfer_events_from_block
    ON transfer_events (from_address, block_number DESC, tx_id DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transfer_events_token_block
    ON transfer_events (token_address, block_number DESC, tx_id DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transfer_events_to_token_block
    ON transfer_events (to_address, token_address, block_number DESC, tx_id DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transfer_events_from_token_block
    ON transfer_events (from_address, token_address, block_number DESC, tx_id DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transfer_events_token_event_to_block
    ON transfer_events (token_address, event_type, to_address, block_number DESC, tx_id DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transfer_events_token_event_from_block
    ON transfer_events (token_address, event_type, from_address, block_number DESC, tx_id DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_transfer_events_event_to_from_token_block
    ON transfer_events (event_type, to_address, from_address, token_address, block_number, tx_id, id);

-- Index for block number + addresses queries
CREATE INDEX IF NOT EXISTS idx_transfer_events_block_to_from
    ON transfer_events (block_number, to_address, from_address);

------------------------------------------------------------
-- FUNGIBLE_TOKEN_INTERACTIONS TABLE (non-versioned, append-only)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS fungible_token_interactions (
    id TEXT PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    contract_address TEXT NOT NULL,
    wallet_address TEXT NOT NULL
);

-- Index for rollback operations
CREATE INDEX IF NOT EXISTS idx_fungible_token_interactions_block_number
    ON fungible_token_interactions (block_number);

-- Index for wallet address queries
CREATE INDEX IF NOT EXISTS idx_fungible_token_interactions_wallet
    ON fungible_token_interactions (wallet_address, contract_address);

-- Index for wallet + contract queries
CREATE INDEX IF NOT EXISTS idx_fungible_token_interactions_wallet_contract
    ON fungible_token_interactions (wallet_address, contract_address, block_number DESC);
