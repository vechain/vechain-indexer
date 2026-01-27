------------------------------------------------------------
-- HISTORY_EVENTS TABLE (non-versioned, append-only)
------------------------------------------------------------

CREATE TABLE IF NOT EXISTS history_events (
    id TEXT PRIMARY KEY,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    tx_id TEXT NOT NULL,
    origin TEXT,
    gas_payer TEXT,
    reverted BOOLEAN,
    contract_address TEXT,
    token_id TEXT,
    event_name TEXT NOT NULL,
    to_address TEXT,
    from_address TEXT,
    value TEXT,
    app_id TEXT,
    proof JSONB,
    round_id TEXT,
    app_votes JSONB,
    support TEXT,
    vote_power TEXT,
    vote_weight TEXT,
    reason TEXT,
    proposal_id TEXT,
    old_level TEXT,
    new_level TEXT,
    input_token TEXT,
    output_token TEXT,
    input_value TEXT,
    output_value TEXT,
    token_address TEXT,
    level_id TEXT,
    owner TEXT,
    vet_generated_vtho_rewards TEXT,
    delegation_rewards TEXT,
    migrated BOOLEAN,
    autorenew BOOLEAN,
    token_ids JSONB,
    validator TEXT,
    delegation_id TEXT,
    period_claimed BIGINT,
    boosted_blocks TEXT,
    is_blacklisted BOOLEAN
);

-- Index for rollback operations
CREATE INDEX IF NOT EXISTS idx_history_events_block_number
    ON history_events (block_number);

-- Composite indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_history_events_app_id_timestamp
    ON history_events (app_id, block_timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_history_events_to_contract_timestamp
    ON history_events (to_address, contract_address, block_timestamp DESC, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_from_contract_timestamp
    ON history_events (from_address, contract_address, block_timestamp DESC, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_origin_contract_timestamp
    ON history_events (origin, contract_address, block_timestamp DESC, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_contract_from_timestamp_event
    ON history_events (contract_address, from_address, block_timestamp DESC, event_name, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_token_timestamp_event
    ON history_events (token_id, block_timestamp DESC, event_name, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_contract_to_timestamp_event
    ON history_events (contract_address, to_address, block_timestamp DESC, event_name, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_contract_origin_timestamp_event
    ON history_events (contract_address, origin, block_timestamp DESC, event_name, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_event_to_timestamp
    ON history_events (event_name, to_address, block_timestamp DESC, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_contract_owner_timestamp_event
    ON history_events (contract_address, owner, block_timestamp DESC, event_name, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_app_event_to
    ON history_events (app_id, event_name, to_address, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_contract_gaspayer_timestamp_event
    ON history_events (contract_address, gas_payer, block_timestamp DESC, event_name, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_gaspayer_timestamp_event
    ON history_events (gas_payer, block_timestamp DESC, event_name, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_from_timestamp_event
    ON history_events (from_address, block_timestamp DESC, event_name, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_to_timestamp_event
    ON history_events (to_address, block_timestamp DESC, event_name, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_origin_timestamp_event
    ON history_events (origin, block_timestamp DESC, event_name, is_blacklisted);

CREATE INDEX IF NOT EXISTS idx_history_events_owner_timestamp_event
    ON history_events (owner, block_timestamp DESC, event_name, is_blacklisted);

-- Index for blacklist updates
CREATE INDEX IF NOT EXISTS idx_history_events_contract_address
    ON history_events (contract_address);
