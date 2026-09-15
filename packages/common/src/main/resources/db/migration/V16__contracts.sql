-- One row per (contract, block) state; superseded_at NULL marks the current row.

CREATE SCHEMA IF NOT EXISTS contracts;

CREATE TABLE contracts.state (
  address                 BYTEA  NOT NULL,
  block_number            BIGINT NOT NULL,
  block_id                BYTEA  NOT NULL,
  block_timestamp         BIGINT NOT NULL,
  created_on              BIGINT NOT NULL,
  deployment_tx_id        BYTEA  NOT NULL,
  deployment_clause_index BIGINT NOT NULL,
  master                  BYTEA  NOT NULL,
  is_erc20                BOOLEAN,
  is_erc721               BOOLEAN,
  is_erc1155              BOOLEAN,
  superseded_at           BIGINT,
  PRIMARY KEY (address, block_number)
);
-- /contracts/by-master/{address}, ordered by deployment time.
CREATE INDEX state_current_master_idx ON contracts.state (master, created_on, address)
  WHERE superseded_at IS NULL;
CREATE INDEX state_block_idx ON contracts.state (block_number);
CREATE INDEX state_prune_idx ON contracts.state (superseded_at) WHERE superseded_at IS NOT NULL;
