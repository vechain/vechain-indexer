-- One schema per indexer; public keeps the framework tables (flyway_schema_history, indexer_state).

CREATE SCHEMA IF NOT EXISTS blocks;
ALTER TABLE block       SET SCHEMA blocks;
ALTER TABLE transaction SET SCHEMA blocks;
ALTER TABLE clause      SET SCHEMA blocks;
ALTER TABLE event       SET SCHEMA blocks;
ALTER TABLE transfer    SET SCHEMA blocks;

-- Resume point for indexers that do not write a row every block; NULL while none is recorded.
ALTER TABLE indexer_state
  ADD COLUMN checkpoint_block    BIGINT,
  ADD COLUMN checkpoint_block_id BYTEA;
