-- Superseded rows below this block are gone, so the store refuses a rollback to an earlier one.
ALTER TABLE indexer_state ADD COLUMN pruned_below BIGINT;
