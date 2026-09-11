-- One row per (token, block) ownership state; superseded_at NULL marks the current owner.

CREATE SCHEMA IF NOT EXISTS nft;

CREATE TABLE nft.ownership (
  contract_address BYTEA         NOT NULL,
  token_id         NUMERIC(78,0) NOT NULL,
  block_number     BIGINT        NOT NULL,
  id               BYTEA         NOT NULL, -- sha1("contract-tokenId"): public field and sort tiebreak
  owner            BYTEA         NOT NULL,
  tx_id            BYTEA         NOT NULL,
  block_id         BYTEA         NOT NULL,
  block_timestamp  BIGINT        NOT NULL,
  superseded_at    BIGINT,
  PRIMARY KEY (contract_address, token_id, block_number)
);
-- /nfts?address and /nfts/contracts; a tokenId filter goes through the primary key instead.
CREATE INDEX ownership_owner_idx ON nft.ownership (owner, block_number, tx_id, id)
  WHERE superseded_at IS NULL;
CREATE INDEX ownership_owner_contract_idx ON nft.ownership
  (owner, contract_address, block_number, tx_id, id) WHERE superseded_at IS NULL;
CREATE INDEX ownership_block_idx ON nft.ownership (block_number);
CREATE INDEX ownership_prune_idx ON nft.ownership (superseded_at) WHERE superseded_at IS NOT NULL;
