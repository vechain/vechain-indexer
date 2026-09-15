-- Every VET, fungible, NFT and semi-fungible transfer, and the fungible contracts each wallet has
-- touched. Both tables are append-only: a rollback deletes the blocks it undoes.

CREATE SCHEMA IF NOT EXISTS transfers;

-- Mirrors org.vechain.indexer.transfer.TransferEventType.
CREATE TYPE transfers.event_type AS ENUM ('VET', 'FUNGIBLE_TOKEN', 'NFT', 'SEMI_FUNGIBLE_TOKEN');

-- transfer_index is the row's place in its block, so (block_timestamp, transfer_index) orders a
-- page the way the chain did without tx_id or id in the index.
CREATE TABLE transfers.transfer (
  id              BYTEA  PRIMARY KEY, -- sha1, 20 bytes
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  transfer_index  INT    NOT NULL,
  tx_id           BYTEA  NOT NULL,
  from_address    BYTEA  NOT NULL,
  to_address      BYTEA  NOT NULL,
  value           NUMERIC(78,0) NOT NULL,
  token_address   BYTEA,          -- NULL for VET
  token_id        NUMERIC(78,0),
  topics          BYTEA[] NOT NULL,
  event_type      transfers.event_type NOT NULL
);
-- Rollback, /transfers/forBlock, and /transfers/latest over every type.
CREATE INDEX transfer_block_idx ON transfers.transfer (block_number DESC, transfer_index);
-- /transfers/latest?eventType=: one ordered scan per type, merged.
CREATE INDEX transfer_type_block_idx ON transfers.transfer
  (event_type, block_number DESC, transfer_index);
-- /transfers, /transfers/to and /transfers/from, by the address and by the token.
CREATE INDEX transfer_to_idx    ON transfers.transfer (to_address,   block_timestamp, transfer_index);
CREATE INDEX transfer_from_idx  ON transfers.transfer (from_address, block_timestamp, transfer_index);
CREATE INDEX transfer_token_idx ON transfers.transfer (token_address, block_timestamp, transfer_index)
  WHERE token_address IS NOT NULL;

-- The fungible contracts a wallet has sent or received, VTHO aside; the row is the first touch.
CREATE TABLE transfers.token_interaction (
  wallet_address   BYTEA  NOT NULL,
  contract_address BYTEA  NOT NULL,
  block_number     BIGINT NOT NULL,
  block_id         BYTEA  NOT NULL,
  block_timestamp  BIGINT NOT NULL,
  PRIMARY KEY (wallet_address, contract_address)
);
CREATE INDEX token_interaction_block_idx ON transfers.token_interaction (block_number);
