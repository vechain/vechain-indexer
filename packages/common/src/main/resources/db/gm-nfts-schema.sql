------------------------------------------------------------
-- GM NFT TABLE (versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS b3tr_gm_nfts (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    level TEXT NOT NULL,
    attached_node_id TEXT,
    b3tr_donated NUMERIC NOT NULL,
    owner TEXT NOT NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records (optimizes WHERE is_current = true queries)
CREATE INDEX IF NOT EXISTS idx_gm_nfts_owner_current
    ON b3tr_gm_nfts (owner) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_gm_nfts_attached_node_id_current
    ON b3tr_gm_nfts (attached_node_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_gm_nfts_level_current
    ON b3tr_gm_nfts (level) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_gm_nfts_level_owner_current
    ON b3tr_gm_nfts (level, owner) WHERE is_current = true;

-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_gm_nfts_block_number
    ON b3tr_gm_nfts (block_number);
