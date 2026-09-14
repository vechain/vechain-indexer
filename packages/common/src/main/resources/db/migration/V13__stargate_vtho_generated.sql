-- One row per block that issued VTHO, with the cumulative total. time_frames names the periods
-- that closed at that row, which is how the API samples the series at each granularity.

CREATE SCHEMA IF NOT EXISTS stargate_vtho_generated;

CREATE TYPE stargate_vtho_generated.time_frame AS ENUM ('HOUR', 'DAY', 'WEEK', 'MONTH', 'YEAR', 'ALL');

CREATE TABLE stargate_vtho_generated.total_by_block (
  block_number    BIGINT        PRIMARY KEY,
  block_id        BYTEA         NOT NULL,
  block_timestamp BIGINT        NOT NULL,
  total           NUMERIC(78,0) NOT NULL,
  hour_of_day     BIGINT        NOT NULL,
  day_of_month    BIGINT        NOT NULL,
  week_of_year    BIGINT        NOT NULL,
  month           BIGINT        NOT NULL,
  year            BIGINT        NOT NULL,
  time_frames     stargate_vtho_generated.time_frame[] NOT NULL,
  block_total     NUMERIC(78,0),
  hour_total      NUMERIC(78,0),
  day_total       NUMERIC(78,0),
  week_total      NUMERIC(78,0),
  month_total     NUMERIC(78,0),
  year_total      NUMERIC(78,0)
);
-- The API's `at or before` lookups and its paged time-frame series.
CREATE INDEX total_by_block_time_idx ON stargate_vtho_generated.total_by_block (block_timestamp);
CREATE INDEX total_by_block_frames_idx ON stargate_vtho_generated.total_by_block
  USING GIN (time_frames);
