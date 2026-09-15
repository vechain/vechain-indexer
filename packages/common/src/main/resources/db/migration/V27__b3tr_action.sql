-- What B3TR_ActionReward has paid, rolled up six ways: to an entity (a wallet, an app, or
-- everything at once) and to one wallet on one app, each all time, per UTC day and per round.
-- Every table is temporal, one row per (key, block) with superseded_at NULL marking the current one.

CREATE SCHEMA IF NOT EXISTS b3tr_action;

CREATE TYPE b3tr_action.entity_type AS ENUM ('APP', 'USER', 'GLOBAL');

-- entity is the wallet, the app id, or empty for GLOBAL. unique_users is the wallets an app has
-- rewarded, or every rewarded wallet on the GLOBAL row, and 0 on a wallet's own row.
CREATE TABLE b3tr_action.entity_all_time (
  entity_type         b3tr_action.entity_type NOT NULL,
  entity              BYTEA  NOT NULL,
  block_number        BIGINT NOT NULL,
  block_id            BYTEA  NOT NULL,
  block_timestamp     BIGINT NOT NULL,
  actions_rewarded    BIGINT NOT NULL,
  total_reward_amount NUMERIC(78,18) NOT NULL,
  total_impact        JSONB,
  unique_users        BIGINT NOT NULL,
  superseded_at       BIGINT,
  PRIMARY KEY (entity_type, entity, block_number)
);
-- One entity's current row; the two leaderboards, which also rank by counting past a value.
CREATE INDEX entity_all_time_current_idx ON b3tr_action.entity_all_time (entity_type, entity)
  WHERE superseded_at IS NULL;
CREATE INDEX entity_all_time_actions_idx
  ON b3tr_action.entity_all_time (entity_type, actions_rewarded DESC, entity)
  WHERE superseded_at IS NULL;
CREATE INDEX entity_all_time_reward_idx
  ON b3tr_action.entity_all_time (entity_type, total_reward_amount DESC, entity)
  WHERE superseded_at IS NULL;
CREATE INDEX entity_all_time_block_idx ON b3tr_action.entity_all_time (block_number);
CREATE INDEX entity_all_time_prune_idx ON b3tr_action.entity_all_time (superseded_at)
  WHERE superseded_at IS NOT NULL;

CREATE TABLE b3tr_action.entity_daily (
  entity_type         b3tr_action.entity_type NOT NULL,
  entity              BYTEA  NOT NULL,
  date                DATE   NOT NULL,
  block_number        BIGINT NOT NULL,
  block_id            BYTEA  NOT NULL,
  block_timestamp     BIGINT NOT NULL,
  actions_rewarded    BIGINT NOT NULL,
  total_reward_amount NUMERIC(78,18) NOT NULL,
  total_impact        JSONB,
  unique_users        BIGINT NOT NULL,
  superseded_at       BIGINT,
  PRIMARY KEY (entity_type, entity, date, block_number)
);
-- The current index also serves a wallet's day range.
CREATE INDEX entity_daily_current_idx ON b3tr_action.entity_daily (entity_type, entity, date)
  WHERE superseded_at IS NULL;
CREATE INDEX entity_daily_actions_idx
  ON b3tr_action.entity_daily (entity_type, date, actions_rewarded DESC, entity)
  WHERE superseded_at IS NULL;
CREATE INDEX entity_daily_reward_idx
  ON b3tr_action.entity_daily (entity_type, date, total_reward_amount DESC, entity)
  WHERE superseded_at IS NULL;
CREATE INDEX entity_daily_block_idx ON b3tr_action.entity_daily (block_number);
CREATE INDEX entity_daily_prune_idx ON b3tr_action.entity_daily (superseded_at)
  WHERE superseded_at IS NOT NULL;

CREATE TABLE b3tr_action.entity_round (
  entity_type         b3tr_action.entity_type NOT NULL,
  entity              BYTEA  NOT NULL,
  round_id            INT    NOT NULL,
  block_number        BIGINT NOT NULL,
  block_id            BYTEA  NOT NULL,
  block_timestamp     BIGINT NOT NULL,
  actions_rewarded    BIGINT NOT NULL,
  total_reward_amount NUMERIC(78,18) NOT NULL,
  total_impact        JSONB,
  unique_users        BIGINT NOT NULL,
  superseded_at       BIGINT,
  PRIMARY KEY (entity_type, entity, round_id, block_number)
);
CREATE INDEX entity_round_current_idx ON b3tr_action.entity_round (entity_type, entity, round_id)
  WHERE superseded_at IS NULL;
-- Its (entity_type, round_id) prefix also finds the newest round on record.
CREATE INDEX entity_round_actions_idx
  ON b3tr_action.entity_round (entity_type, round_id, actions_rewarded DESC, entity)
  WHERE superseded_at IS NULL;
CREATE INDEX entity_round_reward_idx
  ON b3tr_action.entity_round (entity_type, round_id, total_reward_amount DESC, entity)
  WHERE superseded_at IS NULL;
CREATE INDEX entity_round_block_idx ON b3tr_action.entity_round (block_number);
CREATE INDEX entity_round_prune_idx ON b3tr_action.entity_round (superseded_at)
  WHERE superseded_at IS NOT NULL;

CREATE TABLE b3tr_action.app_user_all_time (
  app_id              BYTEA  NOT NULL,
  wallet              BYTEA  NOT NULL,
  block_number        BIGINT NOT NULL,
  block_id            BYTEA  NOT NULL,
  block_timestamp     BIGINT NOT NULL,
  actions_rewarded    BIGINT NOT NULL,
  total_reward_amount NUMERIC(78,18) NOT NULL,
  total_impact        JSONB,
  superseded_at       BIGINT,
  PRIMARY KEY (app_id, wallet, block_number)
);
-- One pair's current row; the apps a wallet has used; the app's two leaderboards.
CREATE INDEX app_user_all_time_current_idx ON b3tr_action.app_user_all_time (app_id, wallet)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_all_time_wallet_idx ON b3tr_action.app_user_all_time (wallet)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_all_time_actions_idx
  ON b3tr_action.app_user_all_time (app_id, actions_rewarded DESC, wallet)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_all_time_reward_idx
  ON b3tr_action.app_user_all_time (app_id, total_reward_amount DESC, wallet)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_all_time_block_idx ON b3tr_action.app_user_all_time (block_number);
CREATE INDEX app_user_all_time_prune_idx ON b3tr_action.app_user_all_time (superseded_at)
  WHERE superseded_at IS NOT NULL;

CREATE TABLE b3tr_action.app_user_daily (
  app_id              BYTEA  NOT NULL,
  wallet              BYTEA  NOT NULL,
  date                DATE   NOT NULL,
  block_number        BIGINT NOT NULL,
  block_id            BYTEA  NOT NULL,
  block_timestamp     BIGINT NOT NULL,
  actions_rewarded    BIGINT NOT NULL,
  total_reward_amount NUMERIC(78,18) NOT NULL,
  total_impact        JSONB,
  superseded_at       BIGINT,
  PRIMARY KEY (app_id, wallet, date, block_number)
);
CREATE INDEX app_user_daily_current_idx ON b3tr_action.app_user_daily (app_id, wallet, date)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_daily_wallet_idx ON b3tr_action.app_user_daily (wallet, date)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_daily_actions_idx
  ON b3tr_action.app_user_daily (app_id, date, actions_rewarded DESC, wallet)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_daily_reward_idx
  ON b3tr_action.app_user_daily (app_id, date, total_reward_amount DESC, wallet)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_daily_block_idx ON b3tr_action.app_user_daily (block_number);
CREATE INDEX app_user_daily_prune_idx ON b3tr_action.app_user_daily (superseded_at)
  WHERE superseded_at IS NOT NULL;

CREATE TABLE b3tr_action.app_user_round (
  app_id              BYTEA  NOT NULL,
  wallet              BYTEA  NOT NULL,
  round_id            INT    NOT NULL,
  block_number        BIGINT NOT NULL,
  block_id            BYTEA  NOT NULL,
  block_timestamp     BIGINT NOT NULL,
  actions_rewarded    BIGINT NOT NULL,
  total_reward_amount NUMERIC(78,18) NOT NULL,
  total_impact        JSONB,
  superseded_at       BIGINT,
  PRIMARY KEY (app_id, wallet, round_id, block_number)
);
CREATE INDEX app_user_round_current_idx ON b3tr_action.app_user_round (app_id, wallet, round_id)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_round_wallet_idx ON b3tr_action.app_user_round (wallet, round_id)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_round_actions_idx
  ON b3tr_action.app_user_round (app_id, round_id, actions_rewarded DESC, wallet)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_round_reward_idx
  ON b3tr_action.app_user_round (app_id, round_id, total_reward_amount DESC, wallet)
  WHERE superseded_at IS NULL;
CREATE INDEX app_user_round_block_idx ON b3tr_action.app_user_round (block_number);
CREATE INDEX app_user_round_prune_idx ON b3tr_action.app_user_round (superseded_at)
  WHERE superseded_at IS NOT NULL;
