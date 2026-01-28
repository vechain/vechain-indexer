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
