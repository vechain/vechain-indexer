-- One VoteCast stream two ways: the comment as it was cast, and the running weight and voter
-- count per (proposal, support). superseded_at NULL marks a result's current row.

CREATE SCHEMA IF NOT EXISTS vevote;

-- Mirrors org.vechain.indexer.vevote.Support.
CREATE TYPE vevote.support AS ENUM ('AGAINST', 'FOR', 'ABSTAIN');

CREATE TABLE vevote.comment (
  id              BYTEA  PRIMARY KEY, -- sha1(proposal_id, reason), 20 bytes
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  voter           BYTEA  NOT NULL,
  proposal_id     NUMERIC(78,0) NOT NULL,
  support         vevote.support NOT NULL,
  weight          NUMERIC(78,0) NOT NULL,
  reason          TEXT   NOT NULL
);
-- /vevote/proposals/comments, paged by block: unfiltered, by proposal, or by voter.
CREATE INDEX comment_block_idx ON vevote.comment (block_number, id);
CREATE INDEX comment_proposal_idx ON vevote.comment (proposal_id, block_number, id);
CREATE INDEX comment_voter_idx ON vevote.comment (voter, block_number, id);

CREATE TABLE vevote.result (
  proposal_id     NUMERIC(78,0) NOT NULL,
  support         vevote.support NOT NULL,
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  total_weight    NUMERIC(78,0) NOT NULL,
  total_voters    INT    NOT NULL,
  superseded_at   BIGINT,
  PRIMARY KEY (proposal_id, support, block_number)
);
-- The indexer's running total and /vevote/proposal/results, by proposal or by support.
CREATE INDEX result_current_proposal_idx ON vevote.result (proposal_id, support)
  WHERE superseded_at IS NULL;
CREATE INDEX result_current_support_idx ON vevote.result (support, block_number)
  WHERE superseded_at IS NULL;
CREATE INDEX result_block_idx ON vevote.result (block_number);
CREATE INDEX result_prune_idx ON vevote.result (superseded_at) WHERE superseded_at IS NOT NULL;
