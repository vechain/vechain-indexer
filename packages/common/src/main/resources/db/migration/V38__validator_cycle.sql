-- The validator fields delegation, history and token_reward read, one row per change and never
-- pruned, so each reads them as of its own block. The validator resync this version needs fills it.
CREATE TABLE validator.cycle (
  id                   BYTEA            NOT NULL,
  block_number         BIGINT           NOT NULL,
  block_id             BYTEA            NOT NULL,
  block_timestamp      BIGINT           NOT NULL,
  status               validator.status,
  start_block          BIGINT,
  cycle_period_length  BIGINT,
  exit_block           BIGINT,
  completed_periods    BIGINT,
  delegator_vet_staked NUMERIC,
  superseded_at        BIGINT,
  PRIMARY KEY (id, block_number)
);
-- Every validator's id, from which an as-of read probes the key.
CREATE INDEX cycle_current_idx ON validator.cycle (id) WHERE superseded_at IS NULL;
CREATE INDEX cycle_block_idx ON validator.cycle (block_number);
CREATE INDEX cycle_superseded_idx ON validator.cycle (superseded_at) WHERE superseded_at IS NOT NULL;

-- delegation keeps every version now: token_reward reads a validator's delegations as of a block.
DROP INDEX IF EXISTS delegation.state_prune_idx;
DO $$
BEGIN
  -- As V34: inline while small, else DelegationIndexes grows it after start.
  IF (SELECT reltuples FROM pg_class WHERE oid = 'delegation.state'::regclass) BETWEEN 0 AND 5000000 THEN
    CREATE INDEX IF NOT EXISTS state_validator_block_idx ON delegation.state (validator, block_number);
  END IF;
END $$;
