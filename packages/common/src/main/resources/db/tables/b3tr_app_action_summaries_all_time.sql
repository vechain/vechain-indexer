------------------------------------------------------------
-- APP ALL-TIME ACTION SUMMARIES TABLE (versioned)
-- Tracks per-user stats within an app
------------------------------------------------------------

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
