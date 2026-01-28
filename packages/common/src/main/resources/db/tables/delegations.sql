------------------------------------------------------------
-- DELEGATIONS TABLE (versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS delegations (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    validator TEXT NOT NULL,
    token_id TEXT NOT NULL,
    owner TEXT NOT NULL,
    status TEXT NOT NULL,
    token_level TEXT NOT NULL,
    staked_amount TEXT NOT NULL,
    total_rewards_claimed NUMERIC NOT NULL,
    notify BOOLEAN NOT NULL DEFAULT false,
    tx_id TEXT NOT NULL,
    validator_next_cycle BIGINT NOT NULL,
    validator_cycle_length BIGINT NOT NULL,
    force BOOLEAN NOT NULL DEFAULT false,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_delegations_validator_status_current
    ON delegations (validator, status) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_delegations_validator_current
    ON delegations (validator) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_delegations_token_id_current
    ON delegations (token_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_delegations_status_current
    ON delegations (status) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_delegations_status_validator_next_cycle_current
    ON delegations (status, validator_next_cycle) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_delegations_status_token_level_staked_amount_current
    ON delegations (status, token_level, staked_amount) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_delegations_notify_current
    ON delegations (notify) WHERE is_current = true;

-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_delegations_block_number
    ON delegations (block_number);
