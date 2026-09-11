-- One row per (collection, block) state; superseded_at NULL marks the current row, so rollback
-- deletes from a block on and reopens what those rows had closed.

CREATE SCHEMA IF NOT EXISTS nft_blacklist;

CREATE TABLE nft_blacklist.collection_state (
  contract_address BYTEA   NOT NULL,
  block_number     BIGINT  NOT NULL,
  block_id         BYTEA   NOT NULL,
  block_timestamp  BIGINT  NOT NULL,
  is_blacklisted   BOOLEAN NOT NULL,
  superseded_at    BIGINT,
  PRIMARY KEY (contract_address, block_number)
);
-- The anti-join every NFT and history read makes; tens of rows, hashed once per query.
CREATE INDEX collection_state_flagged_idx ON nft_blacklist.collection_state (contract_address)
  WHERE superseded_at IS NULL AND is_blacklisted;
CREATE INDEX collection_state_block_idx ON nft_blacklist.collection_state (block_number);
