-- One row per (token, block) snapshot; superseded_at NULL marks the current row. A burned token
-- keeps its row with the zero address as owner, which the API's "active" reads exclude.

CREATE SCHEMA IF NOT EXISTS stargate_token;

-- Mirrors org.vechain.indexer.validator.Status as a token's delegation state.
CREATE TYPE stargate_token.delegation_status AS ENUM ('NONE', 'QUEUED', 'ACTIVE', 'EXITING', 'EXITED');

CREATE TABLE stargate_token.state (
  token_id                       NUMERIC(78,0) NOT NULL,
  block_number                   BIGINT        NOT NULL,
  block_id                       BYTEA         NOT NULL,
  block_timestamp                BIGINT        NOT NULL,
  level                          TEXT          NOT NULL,
  owner                          BYTEA         NOT NULL,
  manager                        BYTEA,
  delegation_status              stargate_token.delegation_status NOT NULL,
  validator_id                   BYTEA,
  total_rewards_claimed          NUMERIC(78,0) NOT NULL,
  total_bootstrap_rewards_claimed NUMERIC(78,0) NOT NULL,
  vet_staked                     NUMERIC(78,0) NOT NULL,
  migrated                       BOOLEAN       NOT NULL,
  boosted                        BOOLEAN       NOT NULL,
  delegation_next_period         BIGINT,
  delegation_period_length       BIGINT,
  validator_exiting              BOOLEAN,
  superseded_at                  BIGINT,
  PRIMARY KEY (token_id, block_number)
);
-- /stargate/tokens by owner or manager, and the unfiltered page newest first.
CREATE INDEX state_current_owner_idx ON stargate_token.state (owner) WHERE superseded_at IS NULL;
CREATE INDEX state_current_manager_idx ON stargate_token.state (manager) WHERE superseded_at IS NULL;
CREATE INDEX state_current_block_idx ON stargate_token.state (block_number, token_id)
  WHERE superseded_at IS NULL;
-- The indexer's validator-scoped and due-transition lookups.
CREATE INDEX state_current_validator_idx ON stargate_token.state (validator_id)
  WHERE superseded_at IS NULL;
CREATE INDEX state_current_due_idx ON stargate_token.state (delegation_status, delegation_next_period)
  WHERE superseded_at IS NULL;
CREATE INDEX state_block_idx ON stargate_token.state (block_number);
CREATE INDEX state_prune_idx ON stargate_token.state (superseded_at) WHERE superseded_at IS NOT NULL;
