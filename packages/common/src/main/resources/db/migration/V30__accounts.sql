-- The full-block scan from genesis over every account: its overview, its VET balance changelog and
-- the running count of accounts seen. superseded_at NULL marks an address's current overview.

CREATE SCHEMA IF NOT EXISTS accounts;

CREATE TYPE accounts.time_frame AS ENUM ('HOUR', 'DAY', 'WEEK', 'MONTH', 'YEAR', 'ALL');

CREATE TABLE accounts.overview (
  address                 BYTEA  NOT NULL,
  block_number            BIGINT NOT NULL,
  block_id                BYTEA  NOT NULL,
  block_timestamp         BIGINT NOT NULL,
  first_seen              BIGINT NOT NULL,
  last_seen               BIGINT NOT NULL,
  transactions_sent       BIGINT NOT NULL,
  clauses_sent            BIGINT NOT NULL,
  vtho_burned             NUMERIC(78,0) NOT NULL,
  vtho_delegated          NUMERIC(78,0) NOT NULL,
  gas_used                NUMERIC(78,0) NOT NULL,
  vet_sent                NUMERIC(78,0) NOT NULL,
  vet_received            NUMERIC(78,0) NOT NULL,
  vet_balance             NUMERIC(78,0) NOT NULL,
  vtho_block_rewards      NUMERIC(78,0) NOT NULL,
  vtho_passive_generation NUMERIC(78,0) NOT NULL,
  -- When passive VTHO was last credited; NULL until the address first holds VET.
  last_vtho_settlement    BIGINT,
  superseded_at           BIGINT,
  PRIMARY KEY (address, block_number)
);
CREATE INDEX overview_block_idx ON accounts.overview (block_number);
CREATE INDEX overview_prune_idx ON accounts.overview (superseded_at) WHERE superseded_at IS NOT NULL;

-- One row per (address, block) the balance moved at. Block timestamps are unique and ordered like
-- block numbers, so the key doubles as the /accounts/balance/vet/{address} window index.
CREATE TABLE accounts.vet_balance (
  address         BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  balance         NUMERIC(78,0) NOT NULL,
  PRIMARY KEY (address, block_timestamp)
);
CREATE INDEX vet_balance_block_idx ON accounts.vet_balance (block_number);

-- One row per block where the account count moved or a period rolled over.
CREATE TABLE accounts.totals (
  block_number    BIGINT PRIMARY KEY,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  total_accounts  BIGINT NOT NULL,
  time_frames     accounts.time_frame[] NOT NULL
);
-- /accounts/totals picks a resolution per range, then scans that frame by timestamp.
CREATE INDEX totals_time_idx ON accounts.totals (block_timestamp);
CREATE INDEX totals_frames_idx ON accounts.totals USING GIN (time_frames);

-- The block each address was first seen at; total_accounts counts these rows.
CREATE TABLE accounts.seen (
  address      BYTEA  PRIMARY KEY,
  block_number BIGINT NOT NULL
);
CREATE INDEX seen_block_idx ON accounts.seen (block_number);
