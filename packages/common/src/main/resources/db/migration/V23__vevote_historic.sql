-- The two legacy VeVote contracts: a proposal, the options it offered, the tally the contract
-- reported when it was created, the descriptions published for it and one row per voter. Every
-- table carries the block it came from, so one delete per block undoes all of them, and the tally
-- the API serves is counted from the votes at read time.

CREATE SCHEMA IF NOT EXISTS vevote_historic;

CREATE TABLE vevote_historic.proposal (
  contract           BYTEA  NOT NULL,
  proposal_id        NUMERIC(78,0) NOT NULL,
  block_number       BIGINT NOT NULL,
  block_id           BYTEA  NOT NULL,
  block_timestamp    BIGINT NOT NULL,
  proposer           BYTEA,
  title              TEXT,
  description        TEXT,
  proposal_type      INT,
  create_time        BIGINT,
  voting_start_time  BIGINT,
  voting_end_time    BIGINT,
  test               BOOLEAN NOT NULL,
  PRIMARY KEY (contract, proposal_id)
);
-- /vevote/historic-proposals, paged by block and filtered by proposal, contract or the test flag.
CREATE INDEX proposal_block_idx ON vevote_historic.proposal (block_number);
CREATE INDEX proposal_id_idx ON vevote_historic.proposal (proposal_id);

-- The options of a proposal, 1-based as a vote numbers them.
CREATE TABLE vevote_historic.proposal_choice (
  contract    BYTEA  NOT NULL,
  proposal_id NUMERIC(78,0) NOT NULL,
  position    INT    NOT NULL,
  label       TEXT   NOT NULL,
  PRIMARY KEY (contract, proposal_id, position),
  FOREIGN KEY (contract, proposal_id)
    REFERENCES vevote_historic.proposal (contract, proposal_id) ON DELETE CASCADE
);

-- The contract's own count per option, read once at creation and served only to a proposal that
-- has no indexed votes.
CREATE TABLE vevote_historic.proposal_tally (
  contract    BYTEA  NOT NULL,
  proposal_id NUMERIC(78,0) NOT NULL,
  position    INT    NOT NULL,
  votes       BIGINT NOT NULL,
  PRIMARY KEY (contract, proposal_id, position),
  FOREIGN KEY (contract, proposal_id)
    REFERENCES vevote_historic.proposal (contract, proposal_id) ON DELETE CASCADE
);

-- A description the legacy descriptions contract published; the newest is the one served.
CREATE TABLE vevote_historic.description (
  contract     BYTEA  NOT NULL,
  proposal_id  NUMERIC(78,0) NOT NULL,
  block_number BIGINT NOT NULL,
  ipfs_hash    TEXT   NOT NULL,
  PRIMARY KEY (contract, proposal_id, block_number),
  FOREIGN KEY (contract, proposal_id)
    REFERENCES vevote_historic.proposal (contract, proposal_id) ON DELETE CASCADE
);
CREATE INDEX description_block_idx ON vevote_historic.description (block_number);

CREATE TABLE vevote_historic.vote (
  contract        BYTEA  NOT NULL,
  proposal_id     NUMERIC(78,0) NOT NULL,
  voter           BYTEA  NOT NULL,
  block_number    BIGINT NOT NULL,
  block_id        BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  choices         INT[]  NOT NULL,
  PRIMARY KEY (contract, proposal_id, voter)
);
CREATE INDEX vote_block_idx ON vevote_historic.vote (block_number);
