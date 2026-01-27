------------------------------------------------------------
-- BLOCK_USAGE TABLE (non-versioned, append-only)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS block_usage (
    block_number BIGINT PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    cumulative_gas_limit TEXT NOT NULL,
    cumulative_gas_used TEXT NOT NULL,
    cumulative_base_fee_per_gas TEXT,
    cumulative_num_transactions TEXT NOT NULL,
    cumulative_num_clauses TEXT NOT NULL,
    is_hourly BOOLEAN,
    is_daily BOOLEAN,
    is_weekly BOOLEAN,
    is_monthly BOOLEAN
);

-- Index for timestamp range queries
CREATE INDEX IF NOT EXISTS idx_block_usage_timestamp
    ON block_usage (block_timestamp);

-- Index for hourly data queries
CREATE INDEX IF NOT EXISTS idx_block_usage_hourly_timestamp
    ON block_usage (is_hourly, block_timestamp) WHERE is_hourly = true;

-- Index for daily data queries
CREATE INDEX IF NOT EXISTS idx_block_usage_daily_timestamp
    ON block_usage (is_daily, block_timestamp) WHERE is_daily = true;

-- Index for weekly data queries
CREATE INDEX IF NOT EXISTS idx_block_usage_weekly_timestamp
    ON block_usage (is_weekly, block_timestamp) WHERE is_weekly = true;

-- Index for monthly data queries
CREATE INDEX IF NOT EXISTS idx_block_usage_monthly_timestamp
    ON block_usage (is_monthly, block_timestamp) WHERE is_monthly = true;
