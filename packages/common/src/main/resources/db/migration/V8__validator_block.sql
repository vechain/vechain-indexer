-- One row per (block, validator, status): the signer's VALIDATED reward row and a MISSED row per
-- skipped slot. Append-only; each is_* flag marks a validator's first row of an hour/day/week/month.

CREATE SCHEMA IF NOT EXISTS validator_block;

CREATE TYPE validator_block.status AS ENUM ('VALIDATED', 'MISSED');

CREATE TABLE validator_block.slot (
  block_number      BIGINT NOT NULL,
  validator         BYTEA  NOT NULL,
  status            validator_block.status NOT NULL,
  block_id          BYTEA  NOT NULL,
  block_timestamp   BIGINT NOT NULL,
  block_reward      NUMERIC(78,0),
  priority_reward   NUMERIC(78,0),
  total             NUMERIC(78,0),
  delegator_rewards NUMERIC(78,0),
  validator_rewards NUMERIC(78,0),
  is_hourly         BOOLEAN,
  is_daily          BOOLEAN,
  is_weekly         BOOLEAN,
  is_monthly        BOOLEAN,
  PRIMARY KEY (block_number, validator, status)
);
-- /block-rewards filtered by validator and/or status, paged by block; the key serves the unfiltered page.
CREATE INDEX slot_validator_status_block_idx ON validator_block.slot (validator, status, block_number);
CREATE INDEX slot_status_block_idx ON validator_block.slot (status, block_number);
-- /blocks/historic and one validator's slot stats: its rows by time; slot_time_idx for the chain-wide stats.
CREATE INDEX slot_validator_time_idx ON validator_block.slot (validator, status, block_timestamp);
CREATE INDEX slot_time_idx ON validator_block.slot (block_timestamp);
-- The sampled series and the indexer's boundary caches, one per resolution.
CREATE INDEX slot_hourly_idx ON validator_block.slot (validator, block_timestamp)
  WHERE is_hourly AND status = 'VALIDATED';
CREATE INDEX slot_daily_idx ON validator_block.slot (validator, block_timestamp)
  WHERE is_daily AND status = 'VALIDATED';
CREATE INDEX slot_weekly_idx ON validator_block.slot (validator, block_timestamp)
  WHERE is_weekly AND status = 'VALIDATED';
CREATE INDEX slot_monthly_idx ON validator_block.slot (validator, block_timestamp)
  WHERE is_monthly AND status = 'VALIDATED';
