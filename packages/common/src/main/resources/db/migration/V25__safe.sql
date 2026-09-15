-- Every Safe deployed by the canonical factory, and what its owners and its transactions did.
-- The proxy row is the trust root the three other tables reference: an address only enters here
-- through SafeProxyFactory.ProxyCreation, so the foreign key is what the dependsOn edge used to
-- assert. Membership, tx state and tx proposal are temporal, one row per (key, block) state with
-- superseded_at NULL marking the current one; approvals and subcalls are written once.

CREATE SCHEMA IF NOT EXISTS safe;

CREATE TABLE safe.proxy (
  address           BYTEA  PRIMARY KEY,
  singleton         BYTEA  NOT NULL,
  created_block     BIGINT NOT NULL,
  created_timestamp BIGINT NOT NULL,
  vechain_tx_id     BYTEA  NOT NULL,
  block_number      BIGINT NOT NULL,
  block_id          BYTEA  NOT NULL,
  block_timestamp   BIGINT NOT NULL
);
CREATE INDEX proxy_block_idx ON safe.proxy (block_number);

CREATE TABLE safe.membership (
  safe             BYTEA  NOT NULL,
  owner            BYTEA  NOT NULL,
  block_number     BIGINT NOT NULL,
  block_id         BYTEA  NOT NULL,
  block_timestamp  BIGINT NOT NULL,
  added_block      BIGINT NOT NULL,
  added_timestamp  BIGINT NOT NULL,
  removed_block    BIGINT,
  removed_timestamp BIGINT,
  superseded_at    BIGINT,
  PRIMARY KEY (safe, owner, block_number),
  FOREIGN KEY (safe) REFERENCES safe.proxy (address) ON DELETE CASCADE
);
-- /safes/owner/{address}, paged by when the ownership began and narrowed to current or past.
CREATE INDEX membership_current_owner_idx ON safe.membership (owner, added_block)
  WHERE superseded_at IS NULL;
CREATE INDEX membership_block_idx ON safe.membership (block_number);
CREATE INDEX membership_prune_idx ON safe.membership (superseded_at)
  WHERE superseded_at IS NOT NULL;

CREATE TABLE safe.tx_state (
  safe               BYTEA  NOT NULL,
  tx_hash            BYTEA  NOT NULL,
  block_number       BIGINT NOT NULL,
  block_id           BYTEA  NOT NULL,
  block_timestamp    BIGINT NOT NULL,
  executed           BOOLEAN NOT NULL,
  failed             BOOLEAN NOT NULL,
  executor           BYTEA,
  executed_block     BIGINT,
  executed_timestamp BIGINT,
  vechain_tx_id      BYTEA,
  superseded_at      BIGINT,
  PRIMARY KEY (safe, tx_hash, block_number),
  FOREIGN KEY (safe) REFERENCES safe.proxy (address) ON DELETE CASCADE
);
CREATE INDEX tx_state_current_idx ON safe.tx_state (safe, tx_hash) WHERE superseded_at IS NULL;
CREATE INDEX tx_state_block_idx ON safe.tx_state (block_number);
CREATE INDEX tx_state_prune_idx ON safe.tx_state (superseded_at) WHERE superseded_at IS NOT NULL;

-- One owner's approval of a transaction hash; the first one stands, as the appended list did.
CREATE TABLE safe.tx_approval (
  safe            BYTEA  NOT NULL,
  tx_hash         BYTEA  NOT NULL,
  owner           BYTEA  NOT NULL,
  block_number    BIGINT NOT NULL,
  block_timestamp BIGINT NOT NULL,
  vechain_tx_id   BYTEA  NOT NULL,
  PRIMARY KEY (safe, tx_hash, owner),
  FOREIGN KEY (safe) REFERENCES safe.proxy (address) ON DELETE CASCADE
);
CREATE INDEX tx_approval_block_idx ON safe.tx_approval (block_number);

CREATE TABLE safe.tx_proposal (
  safe                  BYTEA  NOT NULL,
  tx_hash               BYTEA  NOT NULL,
  block_number          BIGINT NOT NULL,
  block_id              BYTEA  NOT NULL,
  block_timestamp       BIGINT NOT NULL,
  proposer              BYTEA,
  proposed_block        BIGINT,
  proposed_timestamp    BIGINT,
  proposed_vechain_tx_id BYTEA,
  "to"                  BYTEA,
  value                 NUMERIC(78,0),
  data                  TEXT,
  operation             INT,
  nonce                 NUMERIC(78,0),
  description           TEXT,
  safe_tx_gas           NUMERIC(78,0),
  base_gas              NUMERIC(78,0),
  gas_price             NUMERIC(78,0),
  gas_token             BYTEA,
  refund_receiver       BYTEA,
  superseded_at         BIGINT,
  PRIMARY KEY (safe, tx_hash, block_number),
  FOREIGN KEY (safe) REFERENCES safe.proxy (address) ON DELETE CASCADE
);
-- /safes/{safe}/transactions, newest first, and the single (safe, txHash) lookup.
CREATE INDEX tx_proposal_current_safe_idx ON safe.tx_proposal (safe, block_number)
  WHERE superseded_at IS NULL;
CREATE INDEX tx_proposal_block_idx ON safe.tx_proposal (block_number);
CREATE INDEX tx_proposal_prune_idx ON safe.tx_proposal (superseded_at)
  WHERE superseded_at IS NOT NULL;

-- One entry of a batched proposal, in the order the event listed them.
CREATE TABLE safe.tx_subcall (
  safe         BYTEA  NOT NULL,
  tx_hash      BYTEA  NOT NULL,
  position     INT    NOT NULL,
  block_number BIGINT NOT NULL,
  target       BYTEA  NOT NULL,
  value        NUMERIC(78,0) NOT NULL,
  data         TEXT   NOT NULL,
  operation    INT    NOT NULL,
  label        TEXT   NOT NULL,
  PRIMARY KEY (safe, tx_hash, position),
  FOREIGN KEY (safe) REFERENCES safe.proxy (address) ON DELETE CASCADE
);
CREATE INDEX tx_subcall_block_idx ON safe.tx_subcall (block_number);
