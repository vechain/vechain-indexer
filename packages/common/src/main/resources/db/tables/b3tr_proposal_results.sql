------------------------------------------------------------
-- B3TR PROPOSAL RESULTS TABLE (versioned)
-- Tracks proposal state and voting results
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS b3tr_proposal_results (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    created_at_block_number BIGINT NOT NULL,
    start_round_id INT NOT NULL,
    state TEXT NOT NULL,
    results JSONB NULL,
    description TEXT NOT NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records (optimizes WHERE is_current = true queries)
CREATE INDEX IF NOT EXISTS idx_proposal_results_state_current
    ON b3tr_proposal_results (state) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_proposal_results_state_created_current
    ON b3tr_proposal_results (state, created_at_block_number DESC) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_proposal_results_created_current
    ON b3tr_proposal_results (created_at_block_number DESC) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_proposal_results_block_number
    ON b3tr_proposal_results (block_number);
