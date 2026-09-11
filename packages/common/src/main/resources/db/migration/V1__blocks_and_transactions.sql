-- Hashes and addresses are BYTEA (byte order equals lowercase-hex order), wei amounts NUMERIC(78,0).

CREATE TABLE indexer_state (
  name    TEXT PRIMARY KEY,
  version INT  NOT NULL
);

CREATE TABLE block (
  number            BIGINT PRIMARY KEY,
  id                BYTEA  NOT NULL,
  parent_id         BYTEA  NOT NULL,
  timestamp         BIGINT NOT NULL,
  size              INT    NOT NULL,
  gas_limit         BIGINT NOT NULL,
  gas_used          BIGINT NOT NULL,
  beneficiary       BYTEA  NOT NULL,
  signer            BYTEA  NOT NULL,
  total_score       BIGINT NOT NULL,
  txs_root          BYTEA  NOT NULL,
  txs_features      SMALLINT NOT NULL,
  state_root        BYTEA  NOT NULL,
  receipts_root     BYTEA  NOT NULL,
  com               BOOLEAN NOT NULL,
  base_fee_per_gas  NUMERIC(78,0),
  clause_count      INT    NOT NULL,
  total_vtho_paid   NUMERIC(78,0) NOT NULL,
  -- running totals up to and including this block; serve /transactions/count
  total_transactions           BIGINT NOT NULL,
  total_clauses                BIGINT NOT NULL,
  total_reverted_transactions  BIGINT NOT NULL,
  total_reverted_clauses       BIGINT NOT NULL
);

CREATE TABLE transaction (
  id                        BYTEA  PRIMARY KEY,
  block_number              BIGINT NOT NULL REFERENCES block ON DELETE CASCADE,
  tx_index                  INT    NOT NULL,
  type                      SMALLINT,
  size                      INT    NOT NULL,
  chain_tag                 SMALLINT NOT NULL,
  block_ref                 BYTEA  NOT NULL,
  expiration                BIGINT NOT NULL,
  gas_price_coef            SMALLINT,
  gas                       BIGINT NOT NULL,
  max_fee_per_gas           NUMERIC(78,0),
  max_priority_fee_per_gas  NUMERIC(78,0),
  depends_on                BYTEA,
  nonce                     NUMERIC(20,0) NOT NULL,
  gas_used                  BIGINT NOT NULL,
  gas_payer                 BYTEA  NOT NULL,
  paid                      NUMERIC(78,0) NOT NULL,
  reward                    NUMERIC(78,0) NOT NULL,
  reverted                  BOOLEAN NOT NULL,
  origin                    BYTEA  NOT NULL,
  -- Thor emits one output per clause and none when reverted; outputs carry nothing else of their own
  output_count              SMALLINT NOT NULL
);
-- mixed directions on purpose: /transactions/latest sorts blockNumber DESC, transactionIndex ASC
CREATE UNIQUE INDEX transaction_block_idx ON transaction (block_number DESC, tx_index ASC);
CREATE INDEX transaction_origin_idx    ON transaction (origin,    block_number DESC, id DESC);
CREATE INDEX transaction_gas_payer_idx ON transaction (gas_payer, block_number DESC, id DESC);

CREATE TABLE clause (
  tx_id         BYTEA  NOT NULL REFERENCES transaction ON DELETE CASCADE,
  clause_index  INT    NOT NULL,
  block_number  BIGINT NOT NULL, -- denormalised so /transactions/contract pages from one index
  to_address    BYTEA,           -- NULL = contract creation
  value         NUMERIC(78,0) NOT NULL,
  data          BYTEA  NOT NULL,
  PRIMARY KEY (tx_id, clause_index)
);
CREATE INDEX clause_recipient_idx ON clause (to_address, block_number DESC, tx_id DESC)
  WHERE to_address IS NOT NULL;

CREATE TABLE event (
  tx_id         BYTEA  NOT NULL REFERENCES transaction ON DELETE CASCADE,
  clause_index  INT    NOT NULL,
  event_index   INT    NOT NULL, -- position in today's decoded-then-raw list
  address       BYTEA  NOT NULL,
  topic0        BYTEA, topic1 BYTEA, topic2 BYTEA, topic3 BYTEA, topic4 BYTEA,
  data          BYTEA  NOT NULL,
  name          TEXT,
  params        JSONB,
  PRIMARY KEY (tx_id, clause_index, event_index)
);

CREATE TABLE transfer (
  tx_id           BYTEA  NOT NULL REFERENCES transaction ON DELETE CASCADE,
  clause_index    INT    NOT NULL,
  transfer_index  INT    NOT NULL,
  sender          BYTEA  NOT NULL,
  recipient       BYTEA  NOT NULL,
  amount          NUMERIC(78,0) NOT NULL,
  PRIMARY KEY (tx_id, clause_index, transfer_index)
);

-- Resume is the newest committed block, so a crash loses only unflushed commits; ~2x insert speed.
ALTER ROLE CURRENT_USER SET synchronous_commit = off;
