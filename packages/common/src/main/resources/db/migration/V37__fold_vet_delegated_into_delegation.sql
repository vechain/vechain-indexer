-- vet_delegated's series becomes a table of delegation, written in delegation's transaction.
ALTER TYPE vet_delegated.time_frame SET SCHEMA delegation;
ALTER TABLE vet_delegated.total_by_block SET SCHEMA delegation;
DROP SCHEMA vet_delegated;

-- As V36: delegation replays a block vet_delegated had not committed, inside its prune horizon.
UPDATE public.indexer_state d
SET checkpoint_block = v.checkpoint_block, checkpoint_block_id = v.checkpoint_block_id
FROM public.indexer_state v
WHERE d.name = 'delegation' AND v.name = 'vet_delegated'
  AND v.checkpoint_block < d.checkpoint_block
  AND v.checkpoint_block + 1 >= COALESCE(d.pruned_below, 0);

DELETE FROM public.indexer_state WHERE name = 'vet_delegated';
