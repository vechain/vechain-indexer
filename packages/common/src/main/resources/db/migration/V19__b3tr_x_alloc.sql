-- One row per (round, app, block) state; superseded_at NULL marks the current row. The four
-- amounts are the on-chain uint256 scaled down by 1e18, as the API has always rendered them.

CREATE SCHEMA IF NOT EXISTS b3tr_x_alloc;

CREATE TABLE b3tr_x_alloc.result (
  round_id                  INT    NOT NULL,
  app_id                    BYTEA  NOT NULL,
  block_number              BIGINT NOT NULL,
  block_id                  BYTEA  NOT NULL,
  block_timestamp           BIGINT NOT NULL,
  voters                    BIGINT NOT NULL,
  votes_received            NUMERIC(78,0) NOT NULL,
  total_amount              NUMERIC(78,18),
  unallocated_amount        NUMERIC(78,18),
  team_allocation_amount    NUMERIC(78,18),
  rewards_allocation_amount NUMERIC(78,18),
  superseded_at             BIGINT,
  PRIMARY KEY (round_id, app_id, block_number)
);
-- A round's apps by votes or by earnings, and one app across every round.
CREATE INDEX result_current_round_idx ON b3tr_x_alloc.result (round_id)
  WHERE superseded_at IS NULL;
CREATE INDEX result_current_app_idx ON b3tr_x_alloc.result (app_id, round_id)
  WHERE superseded_at IS NULL;
CREATE INDEX result_block_idx ON b3tr_x_alloc.result (block_number);
CREATE INDEX result_prune_idx ON b3tr_x_alloc.result (superseded_at)
  WHERE superseded_at IS NOT NULL;
