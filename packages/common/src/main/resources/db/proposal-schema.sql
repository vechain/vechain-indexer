------------------------------------------------------------
-- B3TR PROPOSAL TABLES
------------------------------------------------------------

-- Proposal Results (versioned)
-- Tracks proposal state and voting results
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

------------------------------------------------------------
-- Proposal Comments (non-versioned, append-only)
-- Stores individual vote comments from voters
CREATE TABLE IF NOT EXISTS b3tr_proposal_comments (
    id TEXT PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    voter TEXT NOT NULL,
    proposal_id TEXT NOT NULL,
    support TEXT NOT NULL,
    weight NUMERIC NOT NULL,
    power NUMERIC NOT NULL,
    reason TEXT NOT NULL
);

-- Indexes for query patterns
CREATE INDEX IF NOT EXISTS idx_proposal_comments_proposal_id
    ON b3tr_proposal_comments (proposal_id);
CREATE INDEX IF NOT EXISTS idx_proposal_comments_proposal_support
    ON b3tr_proposal_comments (proposal_id, support);
CREATE INDEX IF NOT EXISTS idx_proposal_comments_proposal_voter
    ON b3tr_proposal_comments (proposal_id, voter);
CREATE INDEX IF NOT EXISTS idx_proposal_comments_proposal_voter_support
    ON b3tr_proposal_comments (proposal_id, voter, support);
CREATE INDEX IF NOT EXISTS idx_proposal_comments_voter
    ON b3tr_proposal_comments (voter);
CREATE INDEX IF NOT EXISTS idx_proposal_comments_voter_support
    ON b3tr_proposal_comments (voter, support);
-- Index for rollback operations
CREATE INDEX IF NOT EXISTS idx_proposal_comments_block_number
    ON b3tr_proposal_comments (block_number);
