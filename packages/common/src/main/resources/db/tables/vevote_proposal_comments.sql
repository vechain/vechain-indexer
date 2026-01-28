------------------------------------------------------------
-- VEVOTE_PROPOSAL_COMMENTS TABLE (non-versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS vevote_proposal_comments (
    id TEXT NOT NULL PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    voter TEXT NOT NULL,
    proposal_id TEXT NOT NULL,
    support TEXT NOT NULL,
    weight NUMERIC NOT NULL,
    reason TEXT NOT NULL
);

-- Indexes for comments
CREATE INDEX IF NOT EXISTS idx_vevote_comments_voter
    ON vevote_proposal_comments (voter);
CREATE INDEX IF NOT EXISTS idx_vevote_comments_proposal_id
    ON vevote_proposal_comments (proposal_id);
CREATE INDEX IF NOT EXISTS idx_vevote_comments_support
    ON vevote_proposal_comments (support);
CREATE INDEX IF NOT EXISTS idx_vevote_comments_block_number
    ON vevote_proposal_comments (block_number);
