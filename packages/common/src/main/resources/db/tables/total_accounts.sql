------------------------------------------------------------
-- TOTAL ACCOUNTS TABLE (versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS total_accounts (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    total BIGINT NULL,
    time_frame TEXT NULL,
    day_of_month BIGINT NULL,
    week_of_year BIGINT NULL,
    month BIGINT NULL,
    year BIGINT NULL,
    day_total BIGINT NULL,
    week_total BIGINT NULL,
    month_total BIGINT NULL,
    year_total BIGINT NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_total_accounts_current
    ON total_accounts (entity_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_total_accounts_time_frame_current
    ON total_accounts (time_frame) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_total_accounts_time_frame_timestamp_current
    ON total_accounts (time_frame, block_timestamp DESC) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_total_accounts_block_number
    ON total_accounts (block_number);
