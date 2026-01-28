------------------------------------------------------------
-- STARGATE TOKENS TABLE (versioned)
------------------------------------------------------------
-- Tracks individual Stargate NFT tokens with delegation state

CREATE TABLE IF NOT EXISTS stargate_tokens (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    -- Token info
    token_id TEXT NOT NULL,
    level TEXT NOT NULL,
    owner TEXT NOT NULL,
    manager TEXT NULL,
    delegation_status TEXT NOT NULL,
    validator_id TEXT NULL,
    total_rewards_claimed NUMERIC NOT NULL,
    total_bootstrap_rewards_claimed NUMERIC NOT NULL,
    vet_staked NUMERIC NOT NULL,
    migrated BOOLEAN NOT NULL,
    boosted BOOLEAN NOT NULL,
    delegation_next_period BIGINT NULL,
    delegation_period_length BIGINT NULL,
    validator_exiting BOOLEAN NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_owner_current
    ON stargate_tokens (owner) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_manager_current
    ON stargate_tokens (manager) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_owner_manager_current
    ON stargate_tokens (owner, manager) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_validator_id_current
    ON stargate_tokens (validator_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_delegation_next_period_status_current
    ON stargate_tokens (delegation_next_period, delegation_status) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_block_number
    ON stargate_tokens (block_number);
