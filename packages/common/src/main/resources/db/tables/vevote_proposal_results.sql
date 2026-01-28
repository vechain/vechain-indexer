------------------------------------------------------------
-- VEVOTE_PROPOSAL_RESULTS TABLE (versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS vevote_proposal_results (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    proposal_id TEXT NOT NULL,
    support TEXT NOT NULL,
    total_weight NUMERIC NOT NULL,
    total_voters INT NOT NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_vevote_results_support_current
    ON vevote_proposal_results (support) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_vevote_results_proposal_id_current
    ON vevote_proposal_results (proposal_id) WHERE is_current = true;

-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_vevote_results_block_number
    ON vevote_proposal_results (block_number);
