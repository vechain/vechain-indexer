-- A token's delegation is read from delegation.state; the two indexes on these columns go with them.
ALTER TABLE stargate_token.state
  DROP COLUMN delegation_status,
  DROP COLUMN validator_id,
  DROP COLUMN delegation_next_period,
  DROP COLUMN delegation_period_length,
  DROP COLUMN validator_exiting;

DROP TYPE stargate_token.delegation_status;
