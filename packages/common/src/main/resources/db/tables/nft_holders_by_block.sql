------------------------------------------------------------
-- NFT_HOLDERS_BY_BLOCK TABLE (non-versioned, time-frame document)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS nft_holders_by_block (
    block_number BIGINT NOT NULL PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    total BIGINT NOT NULL,
    by_level JSONB NOT NULL,
    hour_of_day BIGINT NOT NULL,
    day_of_month BIGINT NOT NULL,
    week_of_year BIGINT NOT NULL,
    month BIGINT NOT NULL,
    year BIGINT NOT NULL,
    time_frames JSONB NOT NULL,
    block_total NUMERIC,
    hour_total NUMERIC,
    day_total NUMERIC,
    week_total NUMERIC,
    month_total NUMERIC,
    year_total NUMERIC
);

-- Indexes for time-frame queries
CREATE INDEX IF NOT EXISTS idx_nft_holders_block_timestamp
    ON nft_holders_by_block (block_timestamp);
CREATE INDEX IF NOT EXISTS idx_nft_holders_time_frames
    ON nft_holders_by_block USING GIN (time_frames);
