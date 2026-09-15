-- The full-block scan from genesis: cumulative usage per block, and the daily fee rollup with the
-- origins it counts. superseded_at NULL marks a day's current summary.

CREATE SCHEMA IF NOT EXISTS explorer;

CREATE TYPE explorer.time_frame AS ENUM ('HOUR', 'DAY', 'WEEK', 'MONTH', 'YEAR', 'ALL');

CREATE TABLE explorer.block_usage (
  block_number                BIGINT PRIMARY KEY,
  block_id                    BYTEA  NOT NULL,
  block_timestamp             BIGINT NOT NULL,
  cumulative_gas_limit        NUMERIC(78,0) NOT NULL,
  cumulative_gas_used         NUMERIC(78,0) NOT NULL,
  cumulative_base_fee_per_gas NUMERIC(78,0),
  cumulative_num_transactions NUMERIC(78,0) NOT NULL,
  cumulative_num_clauses      NUMERIC(78,0) NOT NULL,
  time_frames                 explorer.time_frame[] NOT NULL
);
-- /explorer/block-usage picks a resolution per range, then scans that frame by timestamp.
CREATE INDEX block_usage_time_idx ON explorer.block_usage (block_timestamp);
CREATE INDEX block_usage_frames_idx ON explorer.block_usage USING GIN (time_frames);

CREATE TABLE explorer.daily_fees (
  day_start_timestamp   BIGINT NOT NULL,
  block_number          BIGINT NOT NULL,
  block_id              BYTEA  NOT NULL,
  block_timestamp       BIGINT NOT NULL,
  date                  TEXT   NOT NULL,
  total_fees_paid       NUMERIC(78,18) NOT NULL,
  daily_active_users    BIGINT NOT NULL,
  average_fees_per_user NUMERIC(78,12) NOT NULL,
  superseded_at         BIGINT,
  PRIMARY KEY (day_start_timestamp, block_number)
);
-- /explorer/average-fees-per-user reads one summary per day in a window.
CREATE INDEX daily_fees_current_idx ON explorer.daily_fees (day_start_timestamp)
  WHERE superseded_at IS NULL;
CREATE INDEX daily_fees_block_idx ON explorer.daily_fees (block_number);
CREATE INDEX daily_fees_prune_idx ON explorer.daily_fees (superseded_at)
  WHERE superseded_at IS NOT NULL;

-- One row per (day, origin) that has paid a fee; daily_active_users counts them.
CREATE TABLE explorer.daily_origin (
  day_start_timestamp BIGINT NOT NULL,
  origin              BYTEA  NOT NULL,
  block_number        BIGINT NOT NULL,
  PRIMARY KEY (day_start_timestamp, origin)
);
CREATE INDEX daily_origin_block_idx ON explorer.daily_origin (block_number);
