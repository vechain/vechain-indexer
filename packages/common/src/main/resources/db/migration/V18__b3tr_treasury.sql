-- One row per classified treasury B3TR transfer; append-only, so no superseded_at and no prune.

CREATE SCHEMA IF NOT EXISTS b3tr_treasury;

-- Mirrors org.vechain.indexer.b3tr.treasury.TreasuryTransferCategory.
CREATE TYPE b3tr_treasury.category AS ENUM
  ('EMISSION', 'SURPLUS', 'GM_UPGRADE', 'GRANT', 'OUT', 'OTHER');

CREATE TABLE b3tr_treasury.transfer (
  id                BYTEA  PRIMARY KEY, -- sha1, 20 bytes
  block_number      BIGINT NOT NULL,
  block_id          BYTEA  NOT NULL,
  block_timestamp   BIGINT NOT NULL,
  tx_id             BYTEA  NOT NULL,
  from_address      BYTEA  NOT NULL,
  to_address        BYTEA  NOT NULL,
  value             NUMERIC(78,0) NOT NULL,
  category          b3tr_treasury.category NOT NULL,
  label             TEXT   NOT NULL,
  counterparty_name TEXT
);
CREATE INDEX transfer_block_idx ON b3tr_treasury.transfer (block_number);
-- /b3tr/treasury/transfers, paged by time, optionally filtered to one category.
CREATE INDEX transfer_time_idx ON b3tr_treasury.transfer (block_timestamp, tx_id, id);
CREATE INDEX transfer_category_time_idx ON b3tr_treasury.transfer
  (category, block_timestamp, tx_id, id);
