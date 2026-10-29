-- One row per listing state change; superseded_at NULL marks the current one. Whether an open
-- listing can still be bought is the API's join to stargate_token.state, never stored here.

CREATE SCHEMA IF NOT EXISTS stargate_marketplace;

-- Mirrors org.vechain.indexer.stargate.marketplace.ListingStatus.
CREATE TYPE stargate_marketplace.status AS ENUM ('OPEN', 'SOLD', 'CANCELLED');

CREATE TABLE stargate_marketplace.listing (
  sale_id         NUMERIC(78,0) NOT NULL,
  block_number    BIGINT        NOT NULL,
  block_id        BYTEA         NOT NULL,
  block_timestamp BIGINT        NOT NULL,
  tx_id           BYTEA         NOT NULL,
  token_id        NUMERIC(78,0) NOT NULL,
  seller          BYTEA         NOT NULL,
  price           NUMERIC(78,0) NOT NULL,
  payment_token   BYTEA         NOT NULL, -- the zero address is VET
  status          stargate_marketplace.status NOT NULL,
  buyer           BYTEA,                  -- SOLD only
  superseded_at   BIGINT,
  PRIMARY KEY (sale_id, block_number)
);
-- The API's open listings by token; the backfill drops it.
CREATE INDEX listing_open_idx ON stargate_marketplace.listing (token_id)
  WHERE superseded_at IS NULL AND status = 'OPEN';
CREATE INDEX listing_block_idx ON stargate_marketplace.listing (block_number);
CREATE INDEX listing_prune_idx ON stargate_marketplace.listing (superseded_at)
  WHERE superseded_at IS NOT NULL;
