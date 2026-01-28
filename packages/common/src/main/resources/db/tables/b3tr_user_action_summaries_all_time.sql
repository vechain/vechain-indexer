------------------------------------------------------------
-- USER ALL-TIME ACTION SUMMARIES TABLE (versioned)
------------------------------------------------------------

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
