-- The governor's proposals and the comments cast on them. The result is temporal, one row per
-- (proposal, block) state with superseded_at NULL marking the current one; the nested vote results
-- are nine columns, null until the first vote.

CREATE SCHEMA IF NOT EXISTS b3tr_proposal;

-- Mirrors org.vechain.indexer.b3tr.voting.Support.
CREATE TYPE b3tr_proposal.support AS ENUM ('AGAINST', 'FOR', 'ABSTAIN');

-- Mirrors org.vechain.indexer.b3tr.proposal.ProposalState, in ordinal order: the contract returns
-- the ordinal and the API renders the name.
CREATE TYPE b3tr_proposal.state AS ENUM ('Pending', 'Active', 'Canceled', 'Defeated', 'Succeeded',
  'Queued', 'Executed', 'DepositNotMet', 'InDevelopment', 'Completed');

CREATE TABLE b3tr_proposal.result (
  proposal_id             NUMERIC(78,0) NOT NULL,
  block_number            BIGINT NOT NULL,
  block_id                BYTEA  NOT NULL,
  block_timestamp         BIGINT NOT NULL,
  created_at_block_number BIGINT NOT NULL,
  start_round_id          INT    NOT NULL,
  state                   b3tr_proposal.state NOT NULL,
  description             TEXT   NOT NULL,
  for_voters              BIGINT,
  for_weight              NUMERIC(78,0),
  for_power               NUMERIC(78,0),
  against_voters          BIGINT,
  against_weight          NUMERIC(78,0),
  against_power           NUMERIC(78,0),
  abstain_voters          BIGINT,
  abstain_weight          NUMERIC(78,0),
  abstain_power           NUMERIC(78,0),
  superseded_at           BIGINT,
  PRIMARY KEY (proposal_id, block_number)
);
-- One proposal's current row, and /b3tr/v2/proposals/results paged by creation and by state.
CREATE INDEX result_current_idx ON b3tr_proposal.result (proposal_id) WHERE superseded_at IS NULL;
CREATE INDEX result_current_created_idx ON b3tr_proposal.result
  (created_at_block_number, proposal_id) WHERE superseded_at IS NULL;
CREATE INDEX result_current_state_idx ON b3tr_proposal.result
  (state, created_at_block_number, proposal_id) WHERE superseded_at IS NULL;
CREATE INDEX result_block_idx ON b3tr_proposal.result (block_number);
CREATE INDEX result_prune_idx ON b3tr_proposal.result (superseded_at)
  WHERE superseded_at IS NOT NULL;

-- A voter's reason for their vote, kept only when it passes the length and language filters. A
-- re-vote replaces the reason, as the (proposal, voter) document id did.
CREATE TABLE b3tr_proposal.comment (
  proposal_id     NUMERIC(78,0) NOT NULL,
  voter           BYTEA  NOT NULL,
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  support         b3tr_proposal.support NOT NULL,
  weight          NUMERIC(78,0) NOT NULL,
  power           NUMERIC(78,0) NOT NULL,
  reason          TEXT   NOT NULL,
  PRIMARY KEY (proposal_id, voter)
);
-- A proposal's comments and a voter's comments, both paged by block.
CREATE INDEX comment_proposal_idx ON b3tr_proposal.comment (proposal_id, block_number, voter);
CREATE INDEX comment_voter_idx ON b3tr_proposal.comment (voter, block_number, proposal_id);
CREATE INDEX comment_block_idx ON b3tr_proposal.comment (block_number, proposal_id, voter);
