------------------------------------------------------------
-- B3TR PROPOSAL COMMENTS TABLE (non-versioned, append-only)
-- Stores individual vote comments from voters
------------------------------------------------------------

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
