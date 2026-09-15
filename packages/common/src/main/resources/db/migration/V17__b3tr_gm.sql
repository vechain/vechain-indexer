-- One row per (token, block) state; superseded_at NULL marks the current row. A burned token keeps
-- its row with the zero address as owner, which the level counts exclude.

CREATE SCHEMA IF NOT EXISTS b3tr_gm;

-- Mirrors org.vechain.indexer.b3tr.gm.GmLevelName, ALL included: level 0 decodes to it.
CREATE TYPE b3tr_gm.level AS ENUM ('ALL', 'EARTH', 'MOON', 'MERCURY', 'VENUS', 'MARS', 'JUPITER',
  'SATURN', 'URANUS', 'NEPTUNE', 'GALAXY');

CREATE TABLE b3tr_gm.state (
  token_id         NUMERIC(78,0) NOT NULL,
  block_number     BIGINT        NOT NULL,
  block_id         BYTEA         NOT NULL,
  block_timestamp  BIGINT        NOT NULL,
  level            b3tr_gm.level NOT NULL,
  attached_node_id NUMERIC(78,0),
  b3tr_donated     NUMERIC(78,0) NOT NULL,
  owner            BYTEA         NOT NULL,
  superseded_at    BIGINT,
  PRIMARY KEY (token_id, block_number)
);
-- /b3tr/gm/level-overview counts held tokens per level.
CREATE INDEX state_held_level_idx ON b3tr_gm.state (level)
  WHERE superseded_at IS NULL AND owner <> '\x0000000000000000000000000000000000000000'::BYTEA;
CREATE INDEX state_block_idx ON b3tr_gm.state (block_number);
CREATE INDEX state_prune_idx ON b3tr_gm.state (superseded_at) WHERE superseded_at IS NOT NULL;
