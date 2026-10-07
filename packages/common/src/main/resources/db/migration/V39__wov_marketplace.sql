-- WoV marketplace purchases and, per (buyer, payment token), a running total written once
-- per block it changed in. Any window's totals are one running row minus another.

CREATE SCHEMA IF NOT EXISTS wov_marketplace;

-- Mirrors org.vechain.indexer.wov.marketplace.WovMechanism.
CREATE TYPE wov_marketplace.mechanism AS ENUM ('CUSTODIAL', 'NON_CUSTODIAL', 'OFFER', 'AUCTION');

-- One row per completed sale: the audit trail the totals are rebuilt from, never paged.
CREATE TABLE wov_marketplace.sale (
  id               BYTEA  PRIMARY KEY, -- sha1(event id), 20 bytes
  block_number     BIGINT NOT NULL,
  block_id         BYTEA  NOT NULL,
  block_timestamp  BIGINT NOT NULL,
  tx_id            BYTEA  NOT NULL,
  contract_address BYTEA  NOT NULL,
  mechanism        wov_marketplace.mechanism NOT NULL,
  market_id        NUMERIC(78,0) NOT NULL, -- saleId, offerId or auctionId
  nft              BYTEA  NOT NULL,
  token_id         NUMERIC(78,0) NOT NULL,
  buyer            BYTEA  NOT NULL,
  payment_token    BYTEA  NOT NULL, -- the zero address is VET
  price            NUMERIC(78,0) NOT NULL
);
CREATE INDEX sale_block_idx ON wov_marketplace.sale (block_number);

-- The price and token a custodial listing or an auction announced before it settled.
CREATE TABLE wov_marketplace.terms (
  contract_address BYTEA  NOT NULL,
  market_id        NUMERIC(78,0) NOT NULL,
  block_number     BIGINT NOT NULL,
  payment_token    BYTEA  NOT NULL,
  price            NUMERIC(78,0) NOT NULL, -- zero for an auction, whose price is bid
  PRIMARY KEY (contract_address, market_id)
);
CREATE INDEX terms_block_idx ON wov_marketplace.terms (block_number);

-- Cumulative items and spend per (buyer, token) after each block they bought in. Insert-only.
CREATE TABLE wov_marketplace.buyer_running (
  buyer            BYTEA  NOT NULL,
  payment_token    BYTEA  NOT NULL,
  block_number     BIGINT NOT NULL,
  block_timestamp  BIGINT NOT NULL,
  items            BIGINT NOT NULL,
  spend            NUMERIC(78,0) NOT NULL,
  PRIMARY KEY (buyer, payment_token, block_number)
);
CREATE INDEX buyer_running_block_idx ON wov_marketplace.buyer_running (block_number);
-- The API's seek to a buyer's last row before a window boundary; the backfill drops it.
CREATE INDEX buyer_running_ts_idx ON wov_marketplace.buyer_running
  (buyer, payment_token, block_timestamp) INCLUDE (items, spend);

-- The address-ordered spine the API pages over.
CREATE TABLE wov_marketplace.buyer (
  address               BYTEA  PRIMARY KEY,
  first_block_number    BIGINT NOT NULL,
  first_block_timestamp BIGINT NOT NULL
);
CREATE INDEX buyer_block_idx ON wov_marketplace.buyer (first_block_number);

-- How far the schema is indexed, one row per entry; pruned to the newest.
CREATE TABLE wov_marketplace.progress (
  block_number    BIGINT PRIMARY KEY,
  block_timestamp BIGINT NOT NULL
);
