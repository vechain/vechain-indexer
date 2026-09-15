package org.vechain.indexer

/** Shared indexer names and storage unit names (a Mongo collection or a Postgres schema). */
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

    object BLOCKS {
        const val NAME = "BlocksIndexer"
        const val COLLECTION = "blocks"
    }

    object EXPLORER {
        const val NAME = "ExplorerIndexer"
        const val COLLECTION = "explorer"
    }

    object GM_NFT {
        const val NAME = "GmNftIndexer"
        const val COLLECTION = "b3tr_gm_nfts"
    }

    object HISTORIC_PROPOSALS {
        const val NAME = "HistoricProposalsIndexer"
        const val COLLECTION = "vevote_historic"
    }

    object HISTORY {
        const val NAME = "HistoryIndexer"
        const val COLLECTION = "history"
    }

    object NFT {
        const val NAME = "NftIndexer"
        const val COLLECTION = "nft"
    }

    object NFT_BLACKLIST {
        const val NAME = "NftBlacklistIndexer"
        const val COLLECTION = "nft_blacklist"
    }

    object PROPOSAL {
        const val NAME = "ProposalIndexer"
        const val COLLECTION = "b3tr_proposal"
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

    object VEVOTE {
        const val NAME = "VeVoteIndexer"
        const val COLLECTION = "vevote"
    }

    object STARGATE_STAKING {
        const val NAME = "StargateStakingIndexer"
        const val COLLECTION = "stargate_staking"
    }

    object VET_DELEGATED_BY_BLOCK {
        const val NAME = "VetDelegatedByBlockIndexer"
        const val COLLECTION = "vet_delegated"
    }

    object VTHO_CLAIMED {
        const val NAME = "VthoClaimedIndexer"
        const val COLLECTION = "stargate_vtho_claimed"
    }

    object X_ALLOC_RESULT {
        const val NAME = "XAllocResultIndexer"
        const val COLLECTION = "b3tr_x_alloc_results"
    }

    object TREASURY_TRANSFER {
        const val NAME = "TreasuryTransferIndexer"
        const val COLLECTION = "b3tr_treasury_transfers"
    }

    object VALIDATOR {
        const val NAME = "ValidatorIndexer"
        const val COLLECTION = "validator"
    }

    object DELEGATION {
        const val NAME = "DelegationIndexer"
        const val COLLECTION = "delegation"
    }

    object VTHO_GENERATED_BY_BLOCK {
        const val NAME = "VthoGeneratedByBlockIndexer"
        const val COLLECTION = "stargate_vtho_generated"
    }

    object VALIDATOR_BLOCK {
        const val NAME = "ValidatorBlockIndexer"
        const val COLLECTION = "validator_block"
    }

    object STARGATE_TOKEN {
        const val NAME = "StargateTokenIndexer"
        const val COLLECTION = "stargate_token"
    }

    object TOKEN_REWARD {
        const val NAME = "TokenRewardIndexer"
        const val COLLECTION = "token_reward"
    }

    object ACCOUNT_TOTALS_SERIES {
        const val NAME = "AccountTotalsSeriesIndexer"
        const val COLLECTION = "account_totals_series"
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

    object NAVIGATOR {
        const val NAME = "NavigatorIndexer"
        const val COLLECTION = "b3tr_navigators"
    }

    object NAVIGATOR_CITIZEN {
        const val NAME = "NavigatorCitizenIndexer"
        const val COLLECTION = "b3tr_navigator_citizens"
    }

    object NAVIGATOR_DELEGATION_EVENT {
        const val NAME = "NavigatorDelegationEventIndexer"
        const val COLLECTION = "b3tr_navigator_delegation_events"
    }

    object NAVIGATOR_FEE {
        const val NAME = "NavigatorFeeIndexer"
        const val COLLECTION = "b3tr_navigator_fees"
    }

    object NAVIGATOR_OVERVIEW_SUMMARY {
        const val NAME = "NavigatorOverviewSummaryIndexer"
        const val COLLECTION = "b3tr_navigator_overview_summaries"
    }

    object NAVIGATOR_FEE_SUMMARY {
        const val NAME = "NavigatorFeeSummaryIndexer"
        const val COLLECTION = "b3tr_navigator_fee_summaries"
    }

    object B3TR_CHALLENGES {
        const val NAME = "B3trChallengesIndexer"
        const val COLLECTION = "b3tr_challenges"
    }

    object SAFE {
        const val NAME = "SafeIndexer"
        const val COLLECTION = "safe"
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
