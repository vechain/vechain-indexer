------------------------------------------------------------
-- STARGATE VTHO CLAIMED BY ACCOUNT TABLE (versioned)
------------------------------------------------------------
-- Tracks total VTHO rewards claimed per account (and optionally per token)

CREATE TABLE IF NOT EXISTS stargate_vtho_claimed_by_account (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    -- Claim info
    account TEXT NOT NULL,
    token_id TEXT NULL,
    total NUMERIC NOT NULL,
    legacy_rewards NUMERIC NOT NULL,
    delegation_rewards NUMERIC NOT NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_stargate_vtho_claimed_account_current
    ON stargate_vtho_claimed_by_account (account) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_vtho_claimed_account_token_current
    ON stargate_vtho_claimed_by_account (account, token_id) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_stargate_vtho_claimed_block_number
    ON stargate_vtho_claimed_by_account (block_number);
