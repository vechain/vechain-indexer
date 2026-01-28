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
