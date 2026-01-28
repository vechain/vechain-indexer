------------------------------------------------------------
-- CONTRACTS TABLE (versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS contracts (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    created_on BIGINT NOT NULL,
    deployment_tx_id TEXT NOT NULL,
    deployment_clause_index BIGINT NOT NULL,
    master TEXT NOT NULL,
    is_erc20 BOOLEAN,
    is_erc721 BOOLEAN,
    is_erc1155 BOOLEAN,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records (optimizes WHERE is_current = true queries)
CREATE INDEX IF NOT EXISTS idx_contracts_master_created_on_current
    ON contracts (master, created_on DESC) WHERE is_current = true;

-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_contracts_block_number
    ON contracts (block_number);
