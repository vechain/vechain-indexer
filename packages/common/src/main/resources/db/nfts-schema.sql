------------------------------------------------------------
-- NFTS TABLE (versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS nfts (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    token_id TEXT NOT NULL,
    contract_address TEXT NOT NULL,
    owner TEXT NOT NULL,
    tx_id TEXT NOT NULL,
    is_blacklisted BOOLEAN,
    PRIMARY KEY (entity_id, version)
);

-- Unique constraint on contract_address + token_id for current records
CREATE UNIQUE INDEX IF NOT EXISTS idx_nfts_contract_token_current
    ON nfts (contract_address, token_id) WHERE is_current = true;

-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_nfts_block_number
    ON nfts (block_number);

-- Index for blacklist filter
CREATE INDEX IF NOT EXISTS idx_nfts_is_blacklisted
    ON nfts (is_blacklisted) WHERE is_current = true;

-- Composite index for owner queries with pagination
CREATE INDEX IF NOT EXISTS idx_nfts_owner_current
    ON nfts (owner, block_number DESC, tx_id DESC, entity_id DESC)
    WHERE is_current = true AND (is_blacklisted IS NULL OR is_blacklisted = false);

-- Composite index for contract address queries
CREATE INDEX IF NOT EXISTS idx_nfts_contract_current
    ON nfts (contract_address, block_number DESC, tx_id DESC, entity_id DESC)
    WHERE is_current = true AND (is_blacklisted IS NULL OR is_blacklisted = false);

-- Composite index for owner + contract address queries
CREATE INDEX IF NOT EXISTS idx_nfts_owner_contract_current
    ON nfts (owner, contract_address, block_number DESC, tx_id DESC, entity_id DESC)
    WHERE is_current = true AND (is_blacklisted IS NULL OR is_blacklisted = false);

-- Composite index for owner + contract address + token_id queries
CREATE INDEX IF NOT EXISTS idx_nfts_owner_contract_token_current
    ON nfts (owner, contract_address, token_id, block_number DESC, tx_id DESC, entity_id DESC)
    WHERE is_current = true AND (is_blacklisted IS NULL OR is_blacklisted = false);

-- Index for blacklist updates
CREATE INDEX IF NOT EXISTS idx_nfts_contract_address
    ON nfts (contract_address);
