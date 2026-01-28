------------------------------------------------------------
-- APP DAILY ACTION SUMMARIES TABLE (versioned)
------------------------------------------------------------

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
