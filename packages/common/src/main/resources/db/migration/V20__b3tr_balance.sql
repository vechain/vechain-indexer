-- One row per (address, block) balance; superseded_at NULL marks the current row. Balances are
-- wei, and can be negative for an address whose first transfer indexed was an outgoing one.

CREATE SCHEMA IF NOT EXISTS b3tr_balance;

CREATE TABLE b3tr_balance.state (
  address         BYTEA  NOT NULL,
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  vot3_balance    NUMERIC(78,0) NOT NULL,
  b3tr_balance    NUMERIC(78,0) NOT NULL,
  total_balance   NUMERIC(78,0) NOT NULL,
  superseded_at   BIGINT,
  PRIMARY KEY (address, block_number)
);
-- /b3tr/richlist pages and ranks by one of the three balances, holders only.
CREATE INDEX state_holders_total_idx ON b3tr_balance.state (total_balance DESC, address)
  WHERE superseded_at IS NULL AND total_balance > 0;
CREATE INDEX state_holders_vot3_idx ON b3tr_balance.state (vot3_balance DESC, address)
  WHERE superseded_at IS NULL AND vot3_balance > 0;
CREATE INDEX state_holders_b3tr_idx ON b3tr_balance.state (b3tr_balance DESC, address)
  WHERE superseded_at IS NULL AND b3tr_balance > 0;
CREATE INDEX state_block_idx ON b3tr_balance.state (block_number);
CREATE INDEX state_prune_idx ON b3tr_balance.state (superseded_at) WHERE superseded_at IS NOT NULL;
