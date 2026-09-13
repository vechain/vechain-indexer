-- jsonb rejects U+0000, which chain data carries; json keeps the escape, and nothing extracts from this column in SQL.
-- Drop and re-add, not ALTER TYPE: a rewrite outlives the task's liveness grace, and the version bump re-indexes anyway.
ALTER TABLE blocks.event DROP COLUMN params;
ALTER TABLE blocks.event ADD COLUMN params json;
