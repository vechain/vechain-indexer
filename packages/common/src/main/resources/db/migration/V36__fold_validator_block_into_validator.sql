-- validator_block's slots become a table of validator, written in validator's transaction.
ALTER TYPE validator_block.status RENAME TO slot_status;
ALTER TYPE validator_block.slot_status SET SCHEMA validator;
ALTER TABLE validator_block.slot SET SCHEMA validator;
DROP SCHEMA validator_block;

-- Only the old per-block lookup of who just missed a slot read it.
DROP INDEX IF EXISTS validator.state_current_missed_idx;

-- A shutdown between the two commits leaves validator_block a block behind; validator replays it
-- rather than skip its slots, when that is inside validator's prune horizon.
UPDATE public.indexer_state v
SET checkpoint_block = b.checkpoint_block, checkpoint_block_id = b.checkpoint_block_id
FROM public.indexer_state b
WHERE v.name = 'validator' AND b.name = 'validator_block'
  AND b.checkpoint_block < v.checkpoint_block
  AND b.checkpoint_block + 1 >= COALESCE(v.pruned_below, 0);

DELETE FROM public.indexer_state WHERE name = 'validator_block';
