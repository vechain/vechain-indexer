------------------------------------------------------------
-- HISTORIC_PROPOSALS_VOTES TABLE (non-versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS historic_proposals_votes (
    id TEXT NOT NULL PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    proposal_id TEXT NOT NULL,
    contract TEXT NOT NULL,
    choices JSONB NOT NULL
);

-- Indexes for historic proposal votes
CREATE INDEX IF NOT EXISTS idx_historic_votes_proposal_id
    ON historic_proposals_votes (proposal_id);
CREATE INDEX IF NOT EXISTS idx_historic_votes_block_number
    ON historic_proposals_votes (block_number);
CREATE INDEX IF NOT EXISTS idx_historic_votes_proposal_contract
    ON historic_proposals_votes (proposal_id, contract);
