------------------------------------------------------------
-- USER SUMMARY TABLES (3 tables)
------------------------------------------------------------

-- User All-time summaries (versioned)
CREATE TABLE IF NOT EXISTS b3tr_user_action_summaries_all_time (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    entity TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    actions_rewarded BIGINT NOT NULL,
    total_reward_amount NUMERIC NOT NULL,
    total_impact JSONB NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records (optimizes WHERE is_current = true queries)
CREATE INDEX IF NOT EXISTS idx_user_all_time_entity_current
    ON b3tr_user_action_summaries_all_time (entity) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_all_time_entity_type_current
    ON b3tr_user_action_summaries_all_time (entity_type) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_all_time_entity_type_reward_current
    ON b3tr_user_action_summaries_all_time (entity_type, total_reward_amount DESC) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_all_time_entity_type_actions_current
    ON b3tr_user_action_summaries_all_time (entity_type, actions_rewarded DESC) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_user_all_time_block_number
    ON b3tr_user_action_summaries_all_time (block_number);

-- User Daily summaries (versioned)
CREATE TABLE IF NOT EXISTS b3tr_user_action_summaries_daily (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    entity TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    date TEXT NOT NULL,
    actions_rewarded BIGINT NOT NULL,
    total_reward_amount NUMERIC NOT NULL,
    total_impact JSONB NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_user_daily_entity_current
    ON b3tr_user_action_summaries_daily (entity) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_daily_entity_date_current
    ON b3tr_user_action_summaries_daily (entity, date) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_daily_entity_type_date_current
    ON b3tr_user_action_summaries_daily (entity_type, date) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_daily_entity_type_date_reward_current
    ON b3tr_user_action_summaries_daily (entity_type, date, total_reward_amount DESC) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_daily_entity_type_date_actions_current
    ON b3tr_user_action_summaries_daily (entity_type, date, actions_rewarded DESC) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_user_daily_block_number
    ON b3tr_user_action_summaries_daily (block_number);

-- User Round summaries (versioned)
CREATE TABLE IF NOT EXISTS b3tr_user_action_summaries_round (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    entity TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    round_id INT NOT NULL,
    actions_rewarded BIGINT NOT NULL,
    total_reward_amount NUMERIC NOT NULL,
    total_impact JSONB NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_user_round_entity_current
    ON b3tr_user_action_summaries_round (entity) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_round_entity_round_current
    ON b3tr_user_action_summaries_round (entity, round_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_round_entity_type_round_current
    ON b3tr_user_action_summaries_round (entity_type, round_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_round_entity_type_round_reward_current
    ON b3tr_user_action_summaries_round (entity_type, round_id, total_reward_amount DESC) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_user_round_entity_type_round_actions_current
    ON b3tr_user_action_summaries_round (entity_type, round_id, actions_rewarded DESC) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_user_round_block_number
    ON b3tr_user_action_summaries_round (block_number);

------------------------------------------------------------
-- APP SUMMARY TABLES (3 tables)
------------------------------------------------------------

-- App All-time summaries (versioned) - tracks per-user stats within an app
CREATE TABLE IF NOT EXISTS b3tr_app_action_summaries_all_time (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    app_id TEXT NOT NULL,
    user_address TEXT NOT NULL,
    actions_rewarded BIGINT NOT NULL,
    total_reward_amount NUMERIC NOT NULL,
    total_impact JSONB NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_app_all_time_app_id_current
    ON b3tr_app_action_summaries_all_time (app_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_all_time_user_current
    ON b3tr_app_action_summaries_all_time (user_address) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_all_time_app_user_current
    ON b3tr_app_action_summaries_all_time (app_id, user_address) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_all_time_app_reward_current
    ON b3tr_app_action_summaries_all_time (app_id, total_reward_amount DESC) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_all_time_app_actions_current
    ON b3tr_app_action_summaries_all_time (app_id, actions_rewarded DESC) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_app_all_time_block_number
    ON b3tr_app_action_summaries_all_time (block_number);

-- App Daily summaries (versioned)
CREATE TABLE IF NOT EXISTS b3tr_app_action_summaries_daily (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    app_id TEXT NOT NULL,
    user_address TEXT NOT NULL,
    date TEXT NOT NULL,
    actions_rewarded BIGINT NOT NULL,
    total_reward_amount NUMERIC NOT NULL,
    total_impact JSONB NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_app_daily_app_id_date_current
    ON b3tr_app_action_summaries_daily (app_id, date) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_daily_user_date_current
    ON b3tr_app_action_summaries_daily (user_address, date) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_daily_app_user_date_current
    ON b3tr_app_action_summaries_daily (app_id, user_address, date) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_daily_app_date_reward_current
    ON b3tr_app_action_summaries_daily (app_id, date, total_reward_amount DESC) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_daily_app_date_actions_current
    ON b3tr_app_action_summaries_daily (app_id, date, actions_rewarded DESC) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_app_daily_block_number
    ON b3tr_app_action_summaries_daily (block_number);

-- App Round summaries (versioned)
CREATE TABLE IF NOT EXISTS b3tr_app_action_summaries_round (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    app_id TEXT NOT NULL,
    user_address TEXT NOT NULL,
    round_id INT NOT NULL,
    actions_rewarded BIGINT NOT NULL,
    total_reward_amount NUMERIC NOT NULL,
    total_impact JSONB NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_app_round_app_id_round_current
    ON b3tr_app_action_summaries_round (app_id, round_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_round_user_round_current
    ON b3tr_app_action_summaries_round (user_address, round_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_round_app_user_round_current
    ON b3tr_app_action_summaries_round (app_id, user_address, round_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_round_app_round_reward_current
    ON b3tr_app_action_summaries_round (app_id, round_id, total_reward_amount DESC) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_app_round_app_round_actions_current
    ON b3tr_app_action_summaries_round (app_id, round_id, actions_rewarded DESC) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_app_round_block_number
    ON b3tr_app_action_summaries_round (block_number);
