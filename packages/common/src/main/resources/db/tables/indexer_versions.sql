------------------------------------------------------------
-- INDEXER VERSION TRACKING TABLE
------------------------------------------------------------
-- Stores the version and last processed block for each indexer

CREATE TABLE IF NOT EXISTS indexer_versions (
    indexer_name TEXT PRIMARY KEY,
    table_name TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    last_processed_block_id TEXT,
    last_processed_block_number BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for looking up by table name
CREATE INDEX IF NOT EXISTS idx_indexer_versions_table_name ON indexer_versions(table_name);
