-- One row per block where the delegated total moved or a period rolled over. time_frames names the
-- periods that closed at that row, which is how the API samples the series at each granularity.

CREATE SCHEMA IF NOT EXISTS vet_delegated;

CREATE TYPE vet_delegated.time_frame AS ENUM ('HOUR', 'DAY', 'WEEK', 'MONTH', 'YEAR', 'ALL');

CREATE TABLE vet_delegated.total_by_block (
  block_number       BIGINT        PRIMARY KEY,
  block_id           BYTEA         NOT NULL,
  block_timestamp    BIGINT        NOT NULL,
  total              NUMERIC(78,0) NOT NULL,
  total_nft_count    BIGINT        NOT NULL,
  hour_of_day        BIGINT        NOT NULL,
  day_of_month       BIGINT        NOT NULL,
  week_of_year       BIGINT        NOT NULL,
  month              BIGINT        NOT NULL,
  year               BIGINT        NOT NULL,
  time_frames        vet_delegated.time_frame[] NOT NULL,
  block_total        NUMERIC(78,0),
  hour_total         NUMERIC(78,0),
  day_total          NUMERIC(78,0),
  week_total         NUMERIC(78,0),
  month_total        NUMERIC(78,0),
  year_total         NUMERIC(78,0),
  -- The per-level split, as TokenLevel name to amount and to NFT count.
  by_level           JSONB         NOT NULL,
  nft_count_by_level JSONB         NOT NULL
);
-- The API's `at or before` lookups and its paged time-frame series.
CREATE INDEX total_by_block_time_idx ON vet_delegated.total_by_block (block_timestamp);
CREATE INDEX total_by_block_frames_idx ON vet_delegated.total_by_block
  USING GIN (time_frames);
