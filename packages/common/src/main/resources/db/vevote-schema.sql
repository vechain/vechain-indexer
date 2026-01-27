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
