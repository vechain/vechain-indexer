------------------------------------------------------------
-- STARGATE TOKENS TABLE (versioned)
------------------------------------------------------------
-- Tracks individual Stargate NFT tokens with delegation state
CREATE TABLE IF NOT EXISTS stargate_tokens (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    -- Token info
    token_id TEXT NOT NULL,
    level TEXT NOT NULL,
    owner TEXT NOT NULL,
    manager TEXT NULL,
    delegation_status TEXT NOT NULL,
    validator_id TEXT NULL,
    total_rewards_claimed NUMERIC NOT NULL,
    total_bootstrap_rewards_claimed NUMERIC NOT NULL,
    vet_staked NUMERIC NOT NULL,
    migrated BOOLEAN NOT NULL,
    boosted BOOLEAN NOT NULL,
    delegation_next_period BIGINT NULL,
    delegation_period_length BIGINT NULL,
    validator_exiting BOOLEAN NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_owner_current
    ON stargate_tokens (owner) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_manager_current
    ON stargate_tokens (manager) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_owner_manager_current
    ON stargate_tokens (owner, manager) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_validator_id_current
    ON stargate_tokens (validator_id) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_delegation_next_period_status_current
    ON stargate_tokens (delegation_next_period, delegation_status) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_stargate_tokens_block_number
    ON stargate_tokens (block_number);

------------------------------------------------------------
-- STARGATE TOKEN REWARDS TABLE (versioned)
------------------------------------------------------------
-- Tracks rewards for staked Stargate tokens per validator cycle
CREATE TABLE IF NOT EXISTS stargate_token_rewards (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    -- Reward info
    token_id TEXT NOT NULL,
    cycle BIGINT NOT NULL,
    validator TEXT NOT NULL,
    rewards NUMERIC NOT NULL,
    effective_stake NUMERIC NULL,
    reward_period TEXT NOT NULL,
    day_of_month BIGINT NOT NULL,
    week_of_year BIGINT NOT NULL,
    month BIGINT NOT NULL,
    year BIGINT NOT NULL,
    day_reward NUMERIC NULL,
    week_reward NUMERIC NULL,
    month_reward NUMERIC NULL,
    year_reward NUMERIC NULL,
    cycle_reward NUMERIC NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_stargate_token_rewards_validator_period_cycle_current
    ON stargate_token_rewards (validator, reward_period, cycle) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_token_rewards_token_period_validator_current
    ON stargate_token_rewards (token_id, reward_period, validator) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_token_rewards_token_period_current
    ON stargate_token_rewards (token_id, reward_period) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_stargate_token_rewards_block_number
    ON stargate_token_rewards (block_number);

------------------------------------------------------------
-- STARGATE VTHO CLAIMED BY ACCOUNT TABLE (versioned)
------------------------------------------------------------
-- Tracks total VTHO rewards claimed per account (and optionally per token)
CREATE TABLE IF NOT EXISTS stargate_vtho_claimed_by_account (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    -- Claim info
    account TEXT NOT NULL,
    token_id TEXT NULL,
    total NUMERIC NOT NULL,
    legacy_rewards NUMERIC NOT NULL,
    delegation_rewards NUMERIC NOT NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_stargate_vtho_claimed_account_current
    ON stargate_vtho_claimed_by_account (account) WHERE is_current = true;
CREATE INDEX IF NOT EXISTS idx_stargate_vtho_claimed_account_token_current
    ON stargate_vtho_claimed_by_account (account, token_id) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_stargate_vtho_claimed_block_number
    ON stargate_vtho_claimed_by_account (block_number);

------------------------------------------------------------
-- STARGATE NFT OWNER BALANCES TABLE (versioned)
------------------------------------------------------------
-- Tracks number of NFTs held by each owner address
CREATE TABLE IF NOT EXISTS stargate_nft_owner_balances (
    entity_id TEXT NOT NULL,
    version INT NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT true,
    block_id TEXT NOT NULL,
    block_number BIGINT NOT NULL,
    block_timestamp BIGINT NOT NULL,
    -- Balance info
    owner TEXT NOT NULL,
    total BIGINT NOT NULL,
    by_level JSONB NOT NULL,
    PRIMARY KEY (entity_id, version)
);

-- Partial indexes for current records
CREATE INDEX IF NOT EXISTS idx_stargate_nft_owner_balances_owner_current
    ON stargate_nft_owner_balances (owner) WHERE is_current = true;
-- Index for rollback/prune operations
CREATE INDEX IF NOT EXISTS idx_stargate_nft_owner_balances_block_number
    ON stargate_nft_owner_balances (block_number);
