------------------------------------------------------------
-- STARGATE TOKEN REWARDS TABLE (versioned)
------------------------------------------------------------
-- Tracks rewards for staked Stargate tokens per validator cycle

CREATE TABLE IF NOT EXISTS stargate_token_rewards (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    -- Reward info
    token_id TEXT NOT NULL,
    cycle BIGINT NOT NULL,
    validator TEXT NOT NULL,
    rewards NUMERIC NOT NULL,
    effective_stake NUMERIC NULL,
    reward_period TEXT NOT NULL,
    day_of_month BIGINT NOT NULL,
    week_of_year BIGINT NOT NULL,
    month BIGINT NOT NULL,
    year BIGINT NOT NULL,
    day_reward NUMERIC NULL,
    week_reward NUMERIC NULL,
    month_reward NUMERIC NULL,
    year_reward NUMERIC NULL,
    cycle_reward NUMERIC NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_stargate_token_rewards_validator_period_cycle_current
    ON stargate_token_rewards (validator, reward_period, cycle) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_token_rewards_token_period_validator_current
    ON stargate_token_rewards (token_id, reward_period, validator) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_token_rewards_token_period_current
    ON stargate_token_rewards (token_id, reward_period) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_stargate_token_rewards_block_number
    ON stargate_token_rewards (block_number);
