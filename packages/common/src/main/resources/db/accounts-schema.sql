------------------------------------------------------------
-- ACCOUNT OVERVIEW TABLE (versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS account_overviews (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    first_seen BIGINT NOT NULL,
    last_seen BIGINT NOT NULL,
    transactions_sent BIGINT NOT NULL,
    clauses_sent BIGINT NOT NULL,
    vtho_burned NUMERIC NOT NULL,
    vtho_delegated NUMERIC NOT NULL,
    gas_used NUMERIC NOT NULL,
    vet_sent NUMERIC NOT NULL,
    vet_received NUMERIC NOT NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial index for current records (optimizes WHERE is_current = true queries)
CREATE INDEX IF NOT EXISTS idx_account_overviews_current
    ON account_overviews (entity_id) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_account_overviews_block_number
    ON account_overviews (block_number);

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

------------------------------------------------------------
-- VET BALANCE TABLE (non-versioned, append-only)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS vet_balances (
    id TEXT NOT NULL PRIMARY KEY,
    address TEXT NOT NULL,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    balance NUMERIC NOT NULL
);

-- Index for queries by address with timestamp ordering
CREATE INDEX IF NOT EXISTS idx_vet_balances_address_timestamp
    ON vet_balances (address, block_timestamp DESC);
-- Index for rollback operations
CREATE INDEX IF NOT EXISTS idx_vet_balances_block_number
    ON vet_balances (block_number);
