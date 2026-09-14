-- One row per (reward record, block) state; superseded_at NULL marks the current row. The ALL
-- tracker of each (validator, token) accrues every signed block; a closed period is written once.

CREATE SCHEMA IF NOT EXISTS token_reward;

-- Mirrors org.vechain.indexer.stargate.tokenReward.RewardPeriod.
CREATE TYPE token_reward.period AS ENUM ('CYCLE', 'DAY', 'WEEK', 'MONTH', 'YEAR', 'ALL');

CREATE TABLE token_reward.state (
  id              TEXT                NOT NULL,
  block_number    BIGINT              NOT NULL,
  block_id        BYTEA               NOT NULL,
  block_timestamp BIGINT              NOT NULL,
  token_id        NUMERIC(78,0)       NOT NULL,
  cycle           BIGINT              NOT NULL,
  validator       BYTEA               NOT NULL,
  rewards         NUMERIC(78,0)       NOT NULL,
  effective_stake NUMERIC(78,0),
  reward_period   token_reward.period NOT NULL,
  day_of_month    BIGINT              NOT NULL,
  week_of_year    BIGINT              NOT NULL,
  month           BIGINT              NOT NULL,
  year            BIGINT              NOT NULL,
  day_reward      NUMERIC(78,0),
  week_reward     NUMERIC(78,0),
  month_reward    NUMERIC(78,0),
  year_reward     NUMERIC(78,0),
  cycle_reward    NUMERIC(78,0),
  superseded_at   BIGINT,
  PRIMARY KEY (id, block_number)
);
-- /stargate/token-rewards/{tokenId}, optionally by validator and period, paged by time.
CREATE INDEX state_current_token_idx ON token_reward.state (token_id, reward_period, block_timestamp)
  WHERE superseded_at IS NULL;
-- The indexer's reload of a validator's cycle trackers after a restart.
CREATE INDEX state_current_validator_idx ON token_reward.state (validator, reward_period, cycle)
  WHERE superseded_at IS NULL;
CREATE INDEX state_block_idx ON token_reward.state (block_number);
CREATE INDEX state_prune_idx ON token_reward.state (superseded_at) WHERE superseded_at IS NOT NULL;
