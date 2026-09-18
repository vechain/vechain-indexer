-- A block's supersede finds each tracker's current row by id, but the key holds a version per
-- signed block inside the prune window, so it read ~100 rows for each one it closed: 46 ms of the
-- 59 ms a block cost. Inline under 5M rows, else BackfillCoordinator grows it after start.
DO $$
BEGIN
  -- -1 is never-analysed, which on a populated table would outlast the liveness grace.
  IF (SELECT reltuples FROM pg_class WHERE oid = 'token_reward.state'::regclass) BETWEEN 0 AND 5000000 THEN
    CREATE INDEX IF NOT EXISTS state_current_id_idx ON token_reward.state (id) WHERE superseded_at IS NULL;
  END IF;
END $$;
