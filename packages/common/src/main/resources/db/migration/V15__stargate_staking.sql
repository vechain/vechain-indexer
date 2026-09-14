-- The stake and unstake stream three ways: VET staked and NFT holders per block, each with a
-- per-level split as JSONB, and every owner's NFT balance after each block that changed it.

CREATE SCHEMA IF NOT EXISTS stargate_staking;

CREATE TYPE stargate_staking.time_frame AS ENUM ('HOUR', 'DAY', 'WEEK', 'MONTH', 'YEAR', 'ALL');

CREATE TABLE stargate_staking.vet_staked_by_block (
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
  time_frames        stargate_staking.time_frame[] NOT NULL,
  block_total        NUMERIC(78,0),
  hour_total         NUMERIC(78,0),
  day_total          NUMERIC(78,0),
  week_total         NUMERIC(78,0),
  month_total        NUMERIC(78,0),
  year_total         NUMERIC(78,0),
  by_level           JSONB         NOT NULL,
  nft_count_by_level JSONB         NOT NULL
);
-- The API's `at or before` lookups and its paged time-frame series, on both series tables.
CREATE INDEX vet_staked_by_block_time_idx ON stargate_staking.vet_staked_by_block (block_timestamp);
CREATE INDEX vet_staked_by_block_frames_idx ON stargate_staking.vet_staked_by_block
  USING GIN (time_frames);

CREATE TABLE stargate_staking.nft_holders_by_block (
  block_number    BIGINT        PRIMARY KEY,
  block_id        BYTEA         NOT NULL,
  block_timestamp BIGINT        NOT NULL,
  total           BIGINT        NOT NULL,
  hour_of_day     BIGINT        NOT NULL,
  day_of_month    BIGINT        NOT NULL,
  week_of_year    BIGINT        NOT NULL,
  month           BIGINT        NOT NULL,
  year            BIGINT        NOT NULL,
  time_frames     stargate_staking.time_frame[] NOT NULL,
  block_total     NUMERIC(78,0),
  hour_total      NUMERIC(78,0),
  day_total       NUMERIC(78,0),
  week_total      NUMERIC(78,0),
  month_total     NUMERIC(78,0),
  year_total      NUMERIC(78,0),
  by_level        JSONB         NOT NULL
);
CREATE INDEX nft_holders_by_block_time_idx ON stargate_staking.nft_holders_by_block (block_timestamp);
CREATE INDEX nft_holders_by_block_frames_idx ON stargate_staking.nft_holders_by_block
  USING GIN (time_frames);

-- The indexer's own changelog: the newest row per owner below a block is that owner's balance.
CREATE TABLE stargate_staking.owner_balance (
  owner           BYTEA  NOT NULL,
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  total           BIGINT NOT NULL,
  by_level        JSONB  NOT NULL,
  PRIMARY KEY (owner, block_number)
);
CREATE INDEX owner_balance_block_idx ON stargate_staking.owner_balance (block_number);
