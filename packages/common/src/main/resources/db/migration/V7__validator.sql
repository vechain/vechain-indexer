-- One row per (validator, block) state; superseded_at NULL marks the current row. Addresses are
-- BYTEA, VET amounts NUMERIC at the 12-decimal scale the indexer computes them in.

CREATE SCHEMA IF NOT EXISTS validator;

-- Mirrors org.vechain.indexer.validator.Status.
CREATE TYPE validator.status AS ENUM ('NONE', 'QUEUED', 'ACTIVE', 'EXITING', 'EXITED');

CREATE TABLE validator.state (
  id                           BYTEA   NOT NULL,
  block_number                 BIGINT  NOT NULL,
  block_id                     BYTEA   NOT NULL,
  block_timestamp              BIGINT  NOT NULL,
  endorser                     BYTEA,
  beneficiary                  BYTEA,
  status                       validator.status,
  cycle_period_length          BIGINT,
  start_block                  BIGINT,
  exit_block                   BIGINT,
  completed_periods            BIGINT,
  validator_vet_staked         NUMERIC,
  validator_locked_weight      NUMERIC,
  delegator_vet_staked         NUMERIC,
  vet_staked                   NUMERIC,
  validator_queued_vet_staked  NUMERIC,
  queued_vet_staked            NUMERIC,
  exiting_vet_staked           NUMERIC,
  validator_exiting_vet_staked NUMERIC,
  total_next_period_weight     NUMERIC,
  queue_position               BIGINT,
  available_start_block        BIGINT,
  scheduled_slots              BIGINT  NOT NULL,
  proposed_blocks              BIGINT  NOT NULL,
  missed_slots                 BIGINT  NOT NULL,
  last_proposed_block_number   BIGINT,
  last_missed_block_number     BIGINT,
  offline_block                BIGINT,
  superseded_at                BIGINT,
  PRIMARY KEY (id, block_number)
);
-- The current set, in the default /validators order (stake DESC, id as tiebreak).
CREATE INDEX state_current_stake_idx ON validator.state (validator_vet_staked DESC, id)
  WHERE superseded_at IS NULL;
-- ValidatorBlockIndexer's per-block lookup of who just missed a slot.
CREATE INDEX state_current_missed_idx ON validator.state (last_missed_block_number)
  WHERE superseded_at IS NULL;
CREATE INDEX state_block_idx ON validator.state (block_number);
CREATE INDEX state_prune_idx ON validator.state (superseded_at) WHERE superseded_at IS NOT NULL;
