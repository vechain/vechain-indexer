-- A challenge and the wallets in it. Both are temporal, one row per (key, block) state with
-- superseded_at NULL marking the current one. The seven address lists a challenge carried become
-- one member row per (wallet, role), so the counts beside them are a COUNT at read time and a
-- wallet's view of a challenge is a join rather than an aggregation pipeline.

CREATE SCHEMA IF NOT EXISTS b3tr_challenges;

-- Mirror the enums of org.vechain.indexer.b3tr.challenges, in ordinal order: the events carry the
-- ordinal and the API renders the name.
CREATE TYPE b3tr_challenges.kind AS ENUM ('Stake', 'Sponsored');
CREATE TYPE b3tr_challenges.visibility AS ENUM ('Public', 'Private');
CREATE TYPE b3tr_challenges.challenge_type AS ENUM ('MaxActions', 'SplitWin');
CREATE TYPE b3tr_challenges.status AS ENUM ('Pending', 'Active', 'Completed', 'Cancelled',
  'Invalid');
CREATE TYPE b3tr_challenges.settlement_mode AS ENUM ('None', 'TopWinners', 'CreatorRefund',
  'SplitWinCompleted');
CREATE TYPE b3tr_challenges.participant_status AS ENUM ('None', 'Invited', 'Declined', 'Joined');

-- The seven address lists the document carried, each now a role a wallet holds on a challenge.
CREATE TYPE b3tr_challenges.member_role AS ENUM ('PARTICIPANT', 'INVITED', 'DECLINED', 'WINNER',
  'ELIGIBLE_INVITEE', 'CLAIMED', 'REFUNDED');

CREATE TABLE b3tr_challenges.challenge (
  challenge_id     BIGINT NOT NULL,
  block_number     BIGINT NOT NULL,
  block_id         BYTEA  NOT NULL,
  block_timestamp  BIGINT NOT NULL,
  kind             b3tr_challenges.kind NOT NULL,
  visibility       b3tr_challenges.visibility NOT NULL,
  challenge_type   b3tr_challenges.challenge_type NOT NULL,
  on_chain_status  b3tr_challenges.status NOT NULL,
  status           b3tr_challenges.status NOT NULL,
  settlement_mode  b3tr_challenges.settlement_mode NOT NULL,
  creator          BYTEA  NOT NULL,
  title            TEXT   NOT NULL,
  description      TEXT   NOT NULL,
  image_uri        TEXT   NOT NULL,
  metadata_uri     TEXT   NOT NULL,
  stake_amount     NUMERIC(78,0) NOT NULL,
  start_round      INT    NOT NULL,
  end_round        INT    NOT NULL,
  threshold        NUMERIC(78,0) NOT NULL,
  num_winners      INT    NOT NULL,
  winners_claimed  INT    NOT NULL,
  prize_per_winner NUMERIC(78,0) NOT NULL,
  all_apps         BOOLEAN NOT NULL,
  total_prize      NUMERIC(78,0) NOT NULL,
  best_score       NUMERIC(78,0) NOT NULL,
  best_count       INT    NOT NULL,
  payouts_claimed  INT    NOT NULL,
  creator_refunded BOOLEAN NOT NULL,
  end_round_passed BOOLEAN NOT NULL,
  created_at_block_number    BIGINT NOT NULL,
  created_at_block_timestamp BIGINT NOT NULL,
  created_tx_id    BYTEA  NOT NULL,
  superseded_at    BIGINT,
  PRIMARY KEY (challenge_id, block_number)
);
-- The public list, paged whole or by one status, and the round change that re-derives the status
-- of the challenges it moves: its two range predicates are ORed, so each gets its own index.
CREATE INDEX challenge_current_public_idx
  ON b3tr_challenges.challenge (visibility, created_at_block_timestamp, challenge_id)
  WHERE superseded_at IS NULL;
CREATE INDEX challenge_current_public_status_idx
  ON b3tr_challenges.challenge (visibility, status, created_at_block_timestamp, challenge_id)
  WHERE superseded_at IS NULL;
CREATE INDEX challenge_current_start_round_idx ON b3tr_challenges.challenge (start_round)
  WHERE superseded_at IS NULL;
CREATE INDEX challenge_current_end_round_idx ON b3tr_challenges.challenge (end_round)
  WHERE superseded_at IS NULL;
CREATE INDEX challenge_current_idx ON b3tr_challenges.challenge (challenge_id)
  WHERE superseded_at IS NULL;
CREATE INDEX challenge_block_idx ON b3tr_challenges.challenge (block_number);
CREATE INDEX challenge_prune_idx ON b3tr_challenges.challenge (superseded_at)
  WHERE superseded_at IS NOT NULL;

-- present = false is how a role a wallet lost survives a rollback of the block that took it away.
CREATE TABLE b3tr_challenges.challenge_member (
  challenge_id  BIGINT NOT NULL,
  wallet        BYTEA  NOT NULL,
  role          b3tr_challenges.member_role NOT NULL,
  block_number  BIGINT NOT NULL,
  present       BOOLEAN NOT NULL,
  superseded_at BIGINT,
  PRIMARY KEY (challenge_id, wallet, role, block_number)
);
CREATE INDEX challenge_member_current_idx ON b3tr_challenges.challenge_member (challenge_id, role)
  WHERE superseded_at IS NULL AND present;
CREATE INDEX challenge_member_block_idx ON b3tr_challenges.challenge_member (block_number);
CREATE INDEX challenge_member_prune_idx ON b3tr_challenges.challenge_member (superseded_at)
  WHERE superseded_at IS NOT NULL;

-- The apps a challenge counts actions for, fixed at creation.
CREATE TABLE b3tr_challenges.challenge_app (
  challenge_id BIGINT NOT NULL,
  position     INT    NOT NULL,
  app_id       TEXT   NOT NULL,
  block_number BIGINT NOT NULL,
  PRIMARY KEY (challenge_id, position)
);
CREATE INDEX challenge_app_block_idx ON b3tr_challenges.challenge_app (block_number);

CREATE TABLE b3tr_challenges.user_challenge (
  wallet             BYTEA  NOT NULL,
  challenge_id       BIGINT NOT NULL,
  block_number       BIGINT NOT NULL,
  block_id           BYTEA  NOT NULL,
  block_timestamp    BIGINT NOT NULL,
  challenge_created_at_block_timestamp BIGINT NOT NULL,
  participant_status b3tr_challenges.participant_status NOT NULL,
  is_creator         BOOLEAN NOT NULL,
  is_winner          BOOLEAN NOT NULL,
  has_claimed_prize  BOOLEAN NOT NULL,
  has_claimed_refund BOOLEAN NOT NULL,
  superseded_at      BIGINT,
  PRIMARY KEY (wallet, challenge_id, block_number)
);
-- A wallet's challenges, newest challenge first, and the completion fan-out over one challenge.
CREATE INDEX user_challenge_current_wallet_idx
  ON b3tr_challenges.user_challenge (wallet, challenge_created_at_block_timestamp, challenge_id)
  WHERE superseded_at IS NULL;
CREATE INDEX user_challenge_current_challenge_idx
  ON b3tr_challenges.user_challenge (challenge_id) WHERE superseded_at IS NULL;
CREATE INDEX user_challenge_block_idx ON b3tr_challenges.user_challenge (block_number);
CREATE INDEX user_challenge_prune_idx ON b3tr_challenges.user_challenge (superseded_at)
  WHERE superseded_at IS NOT NULL;
