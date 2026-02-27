package org.vechain.indexer

/** Shared indexer names and collection names reused by models, processors, and configs. */
object IndexerNames {
    object APP_ALL_TIME_ACTION_SUMMARY {
        const val NAME = "AppAllTimeActionSummaryIndexer"
        const val COLLECTION = "b3tr_app_action_summaries_all_time"
    }

    object APP_DAILY_ACTION_SUMMARY {
        const val NAME = "AppDailyActionSummaryIndexer"
        const val COLLECTION = "b3tr_app_action_summaries_daily"
    }

    object APP_ROUND_ACTION_SUMMARY {
        const val NAME = "AppRoundActionSummaryIndexer"
        const val COLLECTION = "b3tr_app_action_summaries_round"
    }

    object AUTHORITY_NODE {
        const val NAME = "AuthorityNodeIndexer"
        const val COLLECTION = "authority_nodes"
    }

    object BLOCK_USAGE {
        const val NAME = "BlockUsageIndexer"
        const val COLLECTION = "block_usage"
    }

    object GM_NFT {
        const val NAME = "GmNftIndexer"
        const val COLLECTION = "b3tr_gm_nfts"
    }

    object HISTORIC_PROPOSALS {
        const val NAME = "HistoricProposalsIndexer"
        const val COLLECTION = "historic_proposals"
    }

    object HISTORIC_PROPOSALS_VOTE {
        const val NAME = "HistoricProposalsVoteIndexer"
        const val COLLECTION = "historic_proposals_votes"
    }

    object HISTORY {
        const val NAME = "HistoryIndexer"
        const val COLLECTION = "history_events"
    }

    object NFT {
        const val NAME = "NftIndexer"
        const val COLLECTION = "nfts"
    }

    object NFT_BLACKLIST {
        const val NAME = "NftBlacklistIndexer"
    }

    object NFT_HOLDERS_BY_BLOCK {
        const val NAME = "NftHoldersByBlockIndexer"
        const val COLLECTION = "stargate_total_nft_holders_by_block"
    }

    object PROPOSAL_COMMENT {
        const val NAME = "ProposalCommentIndexer"
        const val COLLECTION = "b3tr_proposal_comments"
    }

    object PROPOSAL_RESULT {
        const val NAME = "ProposalResultIndexer"
        const val COLLECTION = "b3tr_proposal_results"
    }

    object TRANSACTION {
        const val NAME = "TransactionIndexer"
        const val COLLECTION = "transactions"
    }

    object TRANSFER {
        const val NAME = "TransferIndexer"
        const val COLLECTION = "transfer_events"
    }

    object FUNGIBLE_TOKEN_INTERACTIONS {
        const val NAME = "FungibleTokenInteractionsIndexer"
        const val COLLECTION = "fungible_token_interactions"
    }

    object USER_ALL_TIME_ACTION_SUMMARY {
        const val NAME = "UserAllTimeActionSummaryIndexer"
        const val COLLECTION = "b3tr_user_action_summaries_all_time"
    }

    object USER_DAILY_ACTION_SUMMARY {
        const val NAME = "UserDailyActionSummaryIndexer"
        const val COLLECTION = "b3tr_user_action_summaries_daily"
    }

    object USER_ROUND_ACTION_SUMMARY {
        const val NAME = "UserRoundActionSummaryIndexer"
        const val COLLECTION = "b3tr_user_action_summaries_round"
    }

    object VEVOTE_COMMENT {
        const val NAME = "VeVoteCommentIndexer"
        const val COLLECTION = "vevote_proposal_comments"
    }

    object VEVOTE_RESULT {
        const val NAME = "VeVoteResultIndexer"
        const val COLLECTION = "vevote_proposal_results"
    }

    object VET_STAKED_BY_BLOCK {
        const val NAME = "VetStakedByBlockIndexer"
        const val COLLECTION = "stargate_total_vet_staked_by_block"
    }

    object VET_DELEGATED_BY_BLOCK {
        const val NAME = "VetDelegatedByBlockIndexer"
        const val COLLECTION = "stargate_total_vet_delegated_by_block"
    }

    object VTHO_CLAIMED_BY_ACCOUNT {
        const val NAME = "VthoClaimedByAccountIndexer"
        const val COLLECTION = "stargate_vtho_claimed_by_account"
    }

    object VTHO_CLAIMED_BY_BLOCK {
        const val NAME = "VthoClaimedByBlockIndexer"
        const val COLLECTION = "stargate_vtho_claimed_by_block"
    }

    object X_ALLOC_RESULT {
        const val NAME = "XAllocResultIndexer"
        const val COLLECTION = "b3tr_x_alloc_results"
    }

    object VALIDATOR {
        const val NAME = "ValidatorIndexer"
        const val COLLECTION = "validators"
    }

    object DELEGATION {
        const val NAME = "DelegationIndexer"
        const val COLLECTION = "delegations"
    }

    object VTHO_GENERATED_BY_BLOCK {
        const val NAME = "VthoGeneratedByBlockIndexer"
        const val COLLECTION = "stargate_vtho_generated_by_block"
    }

    object VALIDATOR_BLOCK {
        const val NAME = "ValidatorBlockIndexer"
        const val COLLECTION = "validator_block_rewards"
    }

    object STARGATE_TOKEN {
        const val NAME = "StargateTokenIndexer"
        const val COLLECTION = "stargate_tokens"
    }

    object TOKEN_REWARD {
        const val NAME = "TokenRewardIndexer"
        const val COLLECTION = "stargate_token_rewards"
    }

    object TOTAL_ACCOUNTS {
        const val NAME = "TotalAccountsIndexer"
        const val COLLECTION = "total_accounts"
    }

    object ACCOUNT_OVERVIEW {
        const val NAME = "AccountOverviewIndexer"
        const val COLLECTION = "account_overviews"
    }

    object CONTRACTS {
        const val NAME = "ContractsIndexer"
        const val COLLECTION = "contracts"
    }

    object VET_BALANCE {
        const val NAME = "VetBalanceIndexer"
        const val COLLECTION = "vet_balances"
    }

    object B3TR_BALANCE {
        const val NAME = "B3trBalanceIndexer"
        const val COLLECTION = "b3tr_balances"
    }

    /** Returns a map of indexer NAME → COLLECTION for every nested object that defines both. */
    fun nameToCollection(): Map<String, String> =
        IndexerNames::class
            .java
            .declaredClasses
            .mapNotNull { clazz ->
                val name =
                    try {
                        clazz.getField("NAME").get(null) as? String
                    } catch (_: Exception) {
                        null
                    }
                val collection =
                    try {
                        clazz.getField("COLLECTION").get(null) as? String
                    } catch (_: Exception) {
                        null
                    }
                if (name != null && collection != null) name to collection else null
            }
            .toMap()
}
