------------------------------------------------------------
-- VALIDATORS TABLE (versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS validators (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    endorser TEXT,
    beneficiary TEXT,
    status TEXT,
    vet_staked NUMERIC,
    validator_vet_staked NUMERIC,
    delegator_vet_staked NUMERIC,
    queued_vet_staked NUMERIC,
    validator_queued_vet_staked NUMERIC,
    delegator_queued_vet_staked NUMERIC,
    validator_exiting_vet_staked NUMERIC,
    delegator_exiting_vet_staked NUMERIC,
    exiting_vet_staked NUMERIC,
    exiting_validator_vet_staked NUMERIC NOT NULL DEFAULT 0,
    cycle_end_block BIGINT,
    total_rewards NUMERIC,
    block_probability NUMERIC,
    blocks_per_epoch NUMERIC,
    total_tvl NUMERIC,
    validator_tvl NUMERIC,
    delegator_tvl NUMERIC,
    validator_tvl_percentage NUMERIC,
    tvl_based_yield NUMERIC,
    validator_yield NUMERIC,
    avg_delegator_yield NUMERIC,
    next_cycle_tvl_based_yield NUMERIC,
    next_cycle_validator_yield NUMERIC,
    next_cycle_avg_delegator_yield NUMERIC,
    nft_yields_next_cycle JSONB,
    total_weight NUMERIC,
    online BOOLEAN,
    completed_periods BIGINT,
    start_block BIGINT,
    cycle_period_length BIGINT,
    blocks_per_year NUMERIC,
    percentage_offline NUMERIC,
    offline_blocks BIGINT,
    exit_block BIGINT,
    queue_position BIGINT,
    available_start_block BIGINT,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_validators_endorser_current
    ON validators (endorser) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_validators_status_current
    ON validators (status) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_validators_validator_tvl_current
    ON validators (validator_tvl DESC) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_validators_delegator_tvl_current
    ON validators (delegator_tvl DESC) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_validators_total_tvl_current
    ON validators (total_tvl DESC) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_validators_block_probability_current
    ON validators (block_probability DESC) WHERE is_current = true;

-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_validators_block_number
    ON validators (block_number);

------------------------------------------------------------
-- VALIDATOR_BLOCK_REWARDS TABLE (non-versioned, append-only)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS validator_block_rewards (
    id TEXT NOT NULL PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    validator TEXT NOT NULL,
    block_reward NUMERIC,
    priority_reward NUMERIC,
    total NUMERIC,
    status TEXT NOT NULL,
    delegator_rewards NUMERIC,
    validator_rewards NUMERIC,
    blocks_offline BIGINT,
    online_block BIGINT,
    is_hourly BOOLEAN,
    is_daily BOOLEAN,
    is_weekly BOOLEAN,
    is_monthly BOOLEAN
);

-- Indexes for validator block rewards
CREATE INDEX IF NOT EXISTS idx_validator_blocks_block_timestamp
    ON validator_block_rewards (block_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_validator_blocks_validator_timestamp
    ON validator_block_rewards (validator, block_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_validator_blocks_validator_block_number
    ON validator_block_rewards (validator, block_number DESC);
CREATE INDEX IF NOT EXISTS idx_validator_blocks_block_number
    ON validator_block_rewards (block_number);
CREATE INDEX IF NOT EXISTS idx_validator_blocks_status_block_number
    ON validator_block_rewards (status, block_number DESC);
-- Composite indexes for time-frame queries
CREATE INDEX IF NOT EXISTS idx_validator_blocks_is_daily_status_validator_timestamp
    ON validator_block_rewards (is_daily, status, validator, block_timestamp);
CREATE INDEX IF NOT EXISTS idx_validator_blocks_is_weekly_status_validator_timestamp
    ON validator_block_rewards (is_weekly, status, validator, block_timestamp);
CREATE INDEX IF NOT EXISTS idx_validator_blocks_is_monthly_status_validator_timestamp
    ON validator_block_rewards (is_monthly, status, validator, block_timestamp);
CREATE INDEX IF NOT EXISTS idx_validator_blocks_status_validator_timestamp
    ON validator_block_rewards (status, validator, block_timestamp);

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
