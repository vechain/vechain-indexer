------------------------------------------------------------
-- STARGATE VET STAKED BY BLOCK TABLE (non-versioned time-series)
------------------------------------------------------------
-- Tracks total VET staked per block with time rollup calculations

CREATE TABLE IF NOT EXISTS stargate_total_vet_staked_by_block (
    block_number BIGINT PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    hour_of_day BIGINT NOT NULL,
    day_of_month BIGINT NOT NULL,
    week_of_year BIGINT NOT NULL,
    month BIGINT NOT NULL,
    year BIGINT NOT NULL,
    time_frames TEXT[] NOT NULL DEFAULT '{}',
    block_total NUMERIC,
    hour_total NUMERIC,
    day_total NUMERIC,
    week_total NUMERIC,
    month_total NUMERIC,
    year_total NUMERIC,
    total NUMERIC NOT NULL,
    by_level JSONB NOT NULL DEFAULT '{}',
    total_nft_count BIGINT NOT NULL DEFAULT 0,
    nft_count_by_level JSONB NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_vet_staked_block_timestamp
    ON stargate_total_vet_staked_by_block (block_timestamp);
CREATE INDEX IF NOT EXISTS idx_vet_staked_time_frames
    ON stargate_total_vet_staked_by_block USING GIN (time_frames);
