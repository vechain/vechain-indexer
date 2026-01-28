------------------------------------------------------------
-- HISTORIC_PROPOSALS TABLE (non-versioned)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS historic_proposals (
    id TEXT NOT NULL PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    proposal_id TEXT NOT NULL,
    contract_address TEXT NOT NULL,
    created_date TEXT NOT NULL,
    proposer TEXT,
    title TEXT,
    description TEXT,
    proposal_type INT,
    choices JSONB,
    test BOOLEAN NOT NULL DEFAULT false,
    create_time BIGINT,
    voting_start_time BIGINT,
    voting_end_time BIGINT,
    vote_tallies JSONB,
    total_votes BIGINT
);

-- Indexes for historic proposals
CREATE INDEX IF NOT EXISTS idx_historic_proposals_proposal_id
    ON historic_proposals (proposal_id);
CREATE INDEX IF NOT EXISTS idx_historic_proposals_contract_address
    ON historic_proposals (contract_address);
CREATE INDEX IF NOT EXISTS idx_historic_proposals_block_number
    ON historic_proposals (block_number);
CREATE INDEX IF NOT EXISTS idx_historic_proposals_test
    ON historic_proposals (test);
