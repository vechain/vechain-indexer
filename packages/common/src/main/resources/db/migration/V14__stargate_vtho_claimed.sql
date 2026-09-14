-- The claim series, one row per block with a claim, and each (account, token)'s running totals as
-- a temporal table; superseded_at NULL marks the current row. An account's total is the sum over
-- its tokens, since every claim names a token.

CREATE SCHEMA IF NOT EXISTS stargate_vtho_claimed;

CREATE TYPE stargate_vtho_claimed.time_frame AS ENUM ('HOUR', 'DAY', 'WEEK', 'MONTH', 'YEAR', 'ALL');

CREATE TABLE stargate_vtho_claimed.total_by_block (
  block_number    BIGINT        PRIMARY KEY,
  block_id        BYTEA         NOT NULL,
  block_timestamp BIGINT        NOT NULL,
  total           NUMERIC(78,0) NOT NULL,
  legacy_rewards  NUMERIC(78,0) NOT NULL,
  hour_of_day     BIGINT        NOT NULL,
  day_of_month    BIGINT        NOT NULL,
  week_of_year    BIGINT        NOT NULL,
  month           BIGINT        NOT NULL,
  year            BIGINT        NOT NULL,
  time_frames     stargate_vtho_claimed.time_frame[] NOT NULL,
  block_total     NUMERIC(78,0),
  hour_total      NUMERIC(78,0),
  day_total       NUMERIC(78,0),
  week_total      NUMERIC(78,0),
  month_total     NUMERIC(78,0),
  year_total      NUMERIC(78,0)
);
-- The API's `at or before` lookups and its paged time-frame series.
CREATE INDEX total_by_block_time_idx ON stargate_vtho_claimed.total_by_block (block_timestamp);
CREATE INDEX total_by_block_frames_idx ON stargate_vtho_claimed.total_by_block
  USING GIN (time_frames);

CREATE TABLE stargate_vtho_claimed.claimed_by_token (
  account            BYTEA         NOT NULL,
  token_id           NUMERIC(78,0) NOT NULL,
  block_number       BIGINT        NOT NULL,
  block_id           BYTEA         NOT NULL,
  block_timestamp    BIGINT        NOT NULL,
  legacy_rewards     NUMERIC(78,0) NOT NULL,
  delegation_rewards NUMERIC(78,0) NOT NULL,
  superseded_at      BIGINT,
  PRIMARY KEY (account, token_id, block_number)
);
CREATE INDEX claimed_by_token_block_idx ON stargate_vtho_claimed.claimed_by_token (block_number);
CREATE INDEX claimed_by_token_prune_idx ON stargate_vtho_claimed.claimed_by_token (superseded_at)
  WHERE superseded_at IS NOT NULL;
