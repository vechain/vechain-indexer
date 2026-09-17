-- The to_address/app_id btrees serve only the API's findActions under event_name = 'B3TR_ACTION',
-- yet every row paid to maintain them: dropped here, at once. Past 5M rows their partial
-- replacements outlast the liveness grace, so HistoryIndexMaintenance builds them after start.
DROP INDEX IF EXISTS history.event_to_name_idx;
DROP INDEX IF EXISTS history.event_to_app_name_idx;
DROP INDEX IF EXISTS history.event_app_name_idx;
DO $$
BEGIN
  IF (SELECT reltuples FROM pg_class WHERE oid = 'history.event'::regclass) <= 5000000 THEN
    CREATE INDEX IF NOT EXISTS event_action_to_idx ON history.event (to_address, block_timestamp, id)
      WHERE event_name = 'B3TR_ACTION';
    CREATE INDEX IF NOT EXISTS event_action_to_app_idx ON history.event (to_address, app_id, block_timestamp, id)
      WHERE event_name = 'B3TR_ACTION';
    CREATE INDEX IF NOT EXISTS event_action_app_idx ON history.event (app_id, block_timestamp, id)
      WHERE event_name = 'B3TR_ACTION';
  END IF;
END $$;
