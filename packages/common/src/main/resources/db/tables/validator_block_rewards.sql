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
