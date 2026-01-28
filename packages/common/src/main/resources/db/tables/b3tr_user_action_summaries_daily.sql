------------------------------------------------------------
-- USER DAILY ACTION SUMMARIES TABLE (versioned)
------------------------------------------------------------

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
