-- One row per (delegation, block) state; superseded_at NULL marks the current row. Ids and token
-- ids are the chain's uint256 values, amounts are wei, token_level is the TokenLevel name.

CREATE SCHEMA IF NOT EXISTS delegation;

-- Mirrors org.vechain.indexer.validator.DelegationStatus.
CREATE TYPE delegation.status AS ENUM ('QUEUED', 'ACTIVE', 'EXITING', 'EXITED');

CREATE TABLE delegation.state (
  id                    NUMERIC(78,0)     NOT NULL,
  block_number          BIGINT            NOT NULL,
  block_id              BYTEA             NOT NULL,
  block_timestamp       BIGINT            NOT NULL,
  validator             BYTEA             NOT NULL,
  token_id              NUMERIC(78,0)     NOT NULL,
  owner                 BYTEA             NOT NULL,
  status                delegation.status NOT NULL,
  token_level           TEXT              NOT NULL,
  staked_amount         NUMERIC(78,0)     NOT NULL,
  total_rewards_claimed NUMERIC(78,0)     NOT NULL,
  tx_id                 BYTEA             NOT NULL,
  transition_at_block   BIGINT,
  initiated_at_block    BIGINT,
  superseded_at         BIGINT,
  PRIMARY KEY (id, block_number)
);
-- The current set by validator and status: /validators/delegations, its facets and token rewards.
CREATE INDEX state_current_validator_idx ON delegation.state (validator, status)
  WHERE superseded_at IS NULL;
-- ...by token: the tokenId filter and the indexer's per-event lookups.
CREATE INDEX state_current_token_idx ON delegation.state (token_id) WHERE superseded_at IS NULL;
-- The indexer's due and zero-cycle scans.
CREATE INDEX state_current_transition_idx ON delegation.state (status, transition_at_block)
  WHERE superseded_at IS NULL;
-- The unfiltered page, newest first.
CREATE INDEX state_current_block_idx ON delegation.state (block_number, id)
  WHERE superseded_at IS NULL;
CREATE INDEX state_block_idx ON delegation.state (block_number);
CREATE INDEX state_prune_idx ON delegation.state (superseded_at) WHERE superseded_at IS NOT NULL;
