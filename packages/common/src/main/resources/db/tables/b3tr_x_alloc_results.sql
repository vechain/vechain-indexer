------------------------------------------------------------
-- B3TR X-ALLOCATION RESULTS TABLE (versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS b3tr_x_alloc_results (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    round_id INT NOT NULL,
    app_id TEXT NOT NULL,
    voters BIGINT NOT NULL,
    votes_received NUMERIC NOT NULL,
    total_amount NUMERIC NULL,
    unallocated_amount NUMERIC NULL,
    team_allocation_amount NUMERIC NULL,
    rewards_allocation_amount NUMERIC NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records (optimizes WHERE is_current = true queries)
CREATE INDEX IF NOT EXISTS idx_x_alloc_results_round_id_current
    ON b3tr_x_alloc_results (round_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_x_alloc_results_app_id_current
    ON b3tr_x_alloc_results (app_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_x_alloc_results_app_round_current
    ON b3tr_x_alloc_results (app_id, round_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_x_alloc_results_total_amount_current
    ON b3tr_x_alloc_results (total_amount DESC) WHERE is_current = true;

-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_x_alloc_results_block_number
    ON b3tr_x_alloc_results (block_number);
