-- searchBy now reaches history.event through history.event_address, so these three have no reader
-- left; dropping them takes a quarter of the btrees off every history.event insert.
DROP INDEX IF EXISTS history.event_origin_idx;
DROP INDEX IF EXISTS history.event_from_idx;
DROP INDEX IF EXISTS history.event_gas_payer_idx;
