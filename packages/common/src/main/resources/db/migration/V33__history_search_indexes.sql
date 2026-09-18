-- V31 rerouted searchBy through history.event_address on the premise that its address index orders
-- the branch. It does, but the role stays a filter on the joined event row, so a field the account
-- rarely holds scans its whole history: /history/{account}?searchBy=to ran 208 s on testnet for a
-- send-only account. Each field is an indexed scan again. Inline under 5M rows, else IndexBuilder.
DO $$
BEGIN
  -- -1 is never-analysed, which on a populated table would outlast the liveness grace.
  IF (SELECT reltuples FROM pg_class WHERE oid = 'history.event'::regclass) BETWEEN 0 AND 5000000 THEN
    CREATE INDEX IF NOT EXISTS event_to_idx        ON history.event (to_address,   block_timestamp, id);
    CREATE INDEX IF NOT EXISTS event_from_idx      ON history.event (from_address, block_timestamp, id);
    CREATE INDEX IF NOT EXISTS event_origin_idx    ON history.event (origin,       block_timestamp, id);
    CREATE INDEX IF NOT EXISTS event_gas_payer_idx ON history.event (gas_payer,    block_timestamp, id);
  END IF;
END $$;
