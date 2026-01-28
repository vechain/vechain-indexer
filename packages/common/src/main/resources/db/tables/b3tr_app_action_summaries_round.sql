------------------------------------------------------------
-- APP ROUND ACTION SUMMARIES TABLE (versioned)
------------------------------------------------------------

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
