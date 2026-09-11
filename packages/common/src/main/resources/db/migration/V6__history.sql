-- One row per business event; history.event_address is the involvedAddresses multikey index made
-- explicit. No is_blacklisted column: readers anti-join nft_blacklist.collection_state instead.

CREATE SCHEMA IF NOT EXISTS history;

-- Mirrors HistoryEventName; extend with ALTER TYPE ... ADD VALUE when the Kotlin enum grows.
CREATE TYPE history.event_name AS ENUM (
  'B3MO_QUEST_CREATED',
  'B3MO_QUEST_JOINED',
  'B3MO_QUEST_REWARD_CLAIMED',
  'B3MO_QUEST_REFUND_CLAIMED',
  'B3MO_QUEST_CREATOR_REFUNDED',
  'B3MO_QUEST_LEFT',
  'B3MO_QUEST_CANCELLED',
  'B3MO_QUEST_DECLINED',
  'B3MO_QUEST_COMPLETED',
  'B3TR_SWAP_VOT3_TO_B3TR',
  'B3TR_SWAP_B3TR_TO_VOT3',
  'B3TR_PROPOSAL_SUPPORT',
  'B3TR_PROPOSAL_WITHDRAW',
  'B3TR_CLAIM_REWARD',
  'B3TR_UPGRADE_GM',
  'B3TR_ACTION',
  'B3TR_PROPOSAL_VOTE',
  'B3TR_XALLOCATION_VOTE',
  'B3TR_NAVIGATOR_DELEGATION_CREATED',
  'B3TR_NAVIGATOR_DELEGATION_INCREASED',
  'B3TR_NAVIGATOR_DELEGATION_DECREASED',
  'B3TR_NAVIGATOR_DELEGATION_REMOVED',
  'B3TR_NAVIGATOR_REGISTERED',
  'B3TR_NAVIGATOR_STAKE_ADDED',
  'B3TR_NAVIGATOR_STAKE_WITHDRAWN',
  'B3TR_NAVIGATOR_SLASHED',
  'B3TR_NAVIGATOR_MINOR_SLASHED',
  'B3TR_NAVIGATOR_FEE_CLAIMED',
  'B3TR_NAVIGATOR_FEE_DEPOSITED',
  'TRANSFER_VET',
  'TRANSFER_FT',
  'TRANSFER_NFT',
  'TRANSFER_SF',
  'SWAP_VET_TO_FT',
  'SWAP_FT_TO_VET',
  'SWAP_FT_TO_FT',
  'UNKNOWN_TX',
  'NFT_SALE',
  'STARGATE_DELEGATE_LEGACY',
  'STARGATE_CLAIM_REWARDS_BASE_LEGACY',
  'STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY',
  'STARGATE_UNDELEGATE_LEGACY',
  'STARGATE_STAKE',
  'STARGATE_UNSTAKE',
  'STARGATE_DELEGATE_ACTIVE',
  'STARGATE_DELEGATE_REQUEST',
  'STARGATE_DELEGATE_EXIT_REQUEST',
  'STARGATE_DELEGATION_EXITED_VALIDATOR',
  'STARGATE_DELEGATION_EXITED',
  'STARGATE_DELEGATE_REQUEST_CANCELLED',
  'STARGATE_CLAIM_REWARDS',
  'STARGATE_BOOST',
  'STARGATE_MANAGER_ADDED',
  'STARGATE_MANAGER_REMOVED',
  'VEVOTE_VOTE_CAST'
);

CREATE TABLE history.event (
  id                         BYTEA  PRIMARY KEY, -- sha1, 20 bytes
  block_number               BIGINT NOT NULL,
  block_id                   BYTEA  NOT NULL,
  block_timestamp            BIGINT NOT NULL,
  tx_id                      BYTEA  NOT NULL,
  event_name                 history.event_name NOT NULL,
  origin                     BYTEA,
  gas_payer                  BYTEA,
  reverted                   BOOLEAN,
  contract_address           BYTEA,
  token_id                   NUMERIC(78,0),
  to_address                 BYTEA,
  from_address               BYTEA,
  owner                      BYTEA,
  value                      NUMERIC(78,0),
  app_id                     BYTEA,
  round_id                   BIGINT,
  proposal_id                NUMERIC(78,0),
  support                    SMALLINT,
  vote_power                 NUMERIC(78,0),
  vote_weight                NUMERIC(78,0),
  reason                     TEXT,
  old_level                  SMALLINT,
  new_level                  SMALLINT,
  level_id                   SMALLINT,
  input_token                BYTEA,
  output_token               BYTEA,
  input_value                NUMERIC(78,0),
  output_value               NUMERIC(78,0),
  vet_generated_vtho_rewards NUMERIC(78,0),
  delegation_rewards         NUMERIC(78,0),
  migrated                   BOOLEAN,
  autorenew                  BOOLEAN,
  validator                  BYTEA,
  delegation_id              NUMERIC(78,0),
  period_claimed             BIGINT,
  boosted_blocks             NUMERIC(78,0),
  proof                      JSONB,          -- SustainabilityProofV2
  app_votes                  JSONB,          -- List<AppVote>
  token_ids                  NUMERIC(78,0)[],
  -- delegation lifecycle bookkeeping, never rendered; read back by ensureLoaded() only
  lifecycle_status           SMALLINT,
  lifecycle_next_cycle       BIGINT,
  lifecycle_cycle_length     BIGINT,
  lifecycle_force_exit       BOOLEAN,
  lifecycle_order            INT
);
CREATE INDEX event_block_idx          ON history.event (block_number);
-- /history/{account}?searchBy=origin|from|gasPayer, one index per field
CREATE INDEX event_origin_idx         ON history.event (origin,       block_timestamp, id);
CREATE INDEX event_from_idx           ON history.event (from_address, block_timestamp, id);
CREATE INDEX event_gas_payer_idx      ON history.event (gas_payer,    block_timestamp, id);
-- /stargate/tokens/{id}/history
CREATE INDEX event_token_idx          ON history.event (token_id, block_timestamp, id);
CREATE INDEX event_token_name_idx     ON history.event (token_id, event_name, block_timestamp, id);
-- /nfts/history
CREATE INDEX event_contract_token_idx ON history.event
  (contract_address, token_id, event_name, block_timestamp, id);
-- /b3tr/actions/users/{wallet}[?appId] and /b3tr/actions/apps/{appId}
CREATE INDEX event_to_name_idx        ON history.event (to_address, event_name, block_timestamp, id);
CREATE INDEX event_to_app_name_idx    ON history.event
  (to_address, app_id, event_name, block_timestamp, id);
CREATE INDEX event_app_name_idx       ON history.event (app_id, event_name, block_timestamp, id);
CREATE INDEX event_lifecycle_idx      ON history.event (delegation_id, block_number, lifecycle_order)
  WHERE lifecycle_status IS NOT NULL;

-- One row per (event, distinct address in origin/gasPayer/to/from/owner). block_timestamp and
-- event_name are denormalised so both account-history shapes are one index range scan, and
-- block_number so rollback is one delete here without a join.
CREATE TABLE history.event_address (
  address         BYTEA  NOT NULL,
  block_timestamp BIGINT NOT NULL,
  event_id        BYTEA  NOT NULL,
  event_name      history.event_name NOT NULL,
  block_number    BIGINT NOT NULL,
  PRIMARY KEY (address, block_timestamp, event_id)
);
CREATE INDEX event_address_name_idx  ON history.event_address
  (address, event_name, block_timestamp, event_id);
CREATE INDEX event_address_block_idx ON history.event_address (block_number);
