-- The VeBetterDAO navigators: each one's state, its citizens' delegations, every delegation event
-- and the fees deposited per round. Navigator, citizen and fee are temporal, one row per
-- (key, block) with superseded_at NULL marking the current one; delegation events are written
-- once. The overview and the fee summaries are sums over the current navigator and fee rows.

CREATE SCHEMA IF NOT EXISTS b3tr_navigator;

CREATE TYPE b3tr_navigator.status AS ENUM ('ACTIVE', 'EXITING', 'DEACTIVATED');

CREATE TABLE b3tr_navigator.navigator (
  address                       BYTEA  NOT NULL,
  block_number                  BIGINT NOT NULL,
  block_id                      BYTEA  NOT NULL,
  block_timestamp               BIGINT NOT NULL,
  status                        b3tr_navigator.status NOT NULL,
  stake                         NUMERIC(78,0) NOT NULL,
  citizen_count                 INT    NOT NULL,
  total_delegated               NUMERIC(78,0) NOT NULL,
  metadata_uri                  TEXT,
  registered_at                 BIGINT NOT NULL,
  exit_announced_round          BIGINT,
  exit_effective_deadline_block BIGINT,
  last_report_round             BIGINT,
  last_report_uri               TEXT,
  superseded_at                 BIGINT,
  PRIMARY KEY (address, block_number)
);
-- The current rows number a few dozen: one index finds a navigator, the list sorts what it scans.
CREATE INDEX navigator_current_idx ON b3tr_navigator.navigator (address)
  WHERE superseded_at IS NULL;
-- The exits that fall due, checked on every block.
CREATE INDEX navigator_exit_idx ON b3tr_navigator.navigator (exit_effective_deadline_block)
  WHERE superseded_at IS NULL AND status = 'EXITING';
CREATE INDEX navigator_block_idx ON b3tr_navigator.navigator (block_number);
CREATE INDEX navigator_prune_idx ON b3tr_navigator.navigator (superseded_at)
  WHERE superseded_at IS NOT NULL;

CREATE TABLE b3tr_navigator.citizen (
  address         BYTEA  NOT NULL,
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  navigator       BYTEA  NOT NULL,
  amount          NUMERIC(78,0) NOT NULL,
  delegated_at    BIGINT NOT NULL,
  active          BOOLEAN NOT NULL,
  superseded_at   BIGINT,
  PRIMARY KEY (address, block_number)
);
CREATE INDEX citizen_current_idx ON b3tr_navigator.citizen (address) WHERE superseded_at IS NULL;
-- /citizens?navigator=, paged by when the delegation began; also the delegations an exit ends.
CREATE INDEX citizen_navigator_idx ON b3tr_navigator.citizen (navigator, delegated_at, address)
  WHERE superseded_at IS NULL AND active;
CREATE INDEX citizen_block_idx ON b3tr_navigator.citizen (block_number);
CREATE INDEX citizen_prune_idx ON b3tr_navigator.citizen (superseded_at)
  WHERE superseded_at IS NOT NULL;

CREATE TABLE b3tr_navigator.delegation_event (
  id              BYTEA  PRIMARY KEY, -- sha1, 20 bytes
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  tx_id           BYTEA  NOT NULL,
  navigator       BYTEA  NOT NULL,
  citizen         BYTEA  NOT NULL,
  event_type      TEXT   NOT NULL,
  amount          NUMERIC(78,0) NOT NULL,
  delta           NUMERIC(78,0) NOT NULL
);
CREATE INDEX delegation_event_block_idx ON b3tr_navigator.delegation_event (block_number);
-- /delegations?navigator= and ?citizen=, paged by time.
CREATE INDEX delegation_event_navigator_idx
  ON b3tr_navigator.delegation_event (navigator, block_timestamp, tx_id, id);
CREATE INDEX delegation_event_citizen_idx
  ON b3tr_navigator.delegation_event (citizen, block_timestamp, tx_id, id);

-- claimed_amount is what FeeClaimed paid out for the round, NULL until it does.
CREATE TABLE b3tr_navigator.fee (
  navigator       BYTEA  NOT NULL,
  round_id        INT    NOT NULL,
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  total_deposited NUMERIC(78,0) NOT NULL,
  claimed_amount  NUMERIC(78,0),
  claimed_at      BIGINT,
  deposited_at    BIGINT NOT NULL,
  unlock_round    BIGINT NOT NULL,
  superseded_at   BIGINT,
  PRIMARY KEY (navigator, round_id, block_number)
);
-- One fee's current row, and /fees/history paged by round.
CREATE INDEX fee_current_idx ON b3tr_navigator.fee (navigator, round_id)
  WHERE superseded_at IS NULL;
CREATE INDEX fee_block_idx ON b3tr_navigator.fee (block_number);
CREATE INDEX fee_prune_idx ON b3tr_navigator.fee (superseded_at) WHERE superseded_at IS NOT NULL;
