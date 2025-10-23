package org.vechain.indexer.utils

import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.transfer.TransferEventType
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorAction

data class BlockDetails(val blockId: String, val blockNumber: Long, val blockTimestamp: Long)

object EventUtils {
    fun determineEventType(params: AbiEventParameters): HistoryEventName? =
        when (params.getEventType()) {
            "B3TR_Vot3ToB3trSwap" -> HistoryEventName.B3TR_SWAP_VOT3_TO_B3TR
            "B3TR_B3trToVot3Swap" -> HistoryEventName.B3TR_SWAP_B3TR_TO_VOT3
            "B3TR_ProposalDeposit" -> HistoryEventName.B3TR_PROPOSAL_SUPPORT
            "B3TR_ClaimReward" -> HistoryEventName.B3TR_CLAIM_REWARD
            "B3TR_ClaimReward_V2" -> HistoryEventName.B3TR_CLAIM_REWARD
            "B3TR_GMUpgrade" -> HistoryEventName.B3TR_UPGRADE_GM
            "B3TR_ActionReward" -> HistoryEventName.B3TR_ACTION
            "B3TR_ProposalVote" -> HistoryEventName.B3TR_PROPOSAL_VOTE
            "B3TR_XAllocationVote" -> HistoryEventName.B3TR_XALLOCATION_VOTE
            "Transfer" -> {
                when {
                    params.params["value"] != null -> HistoryEventName.TRANSFER_FT
                    params.params["tokenId"] != null -> HistoryEventName.TRANSFER_NFT
                    else -> null
                }
            }
            "TransferSingle",
            "TransferBatch" -> HistoryEventName.TRANSFER_SF
            "VET_TRANSFER" -> HistoryEventName.TRANSFER_VET
            "FT_VET_Swap" -> HistoryEventName.SWAP_FT_TO_VET
            "FT_VET_Swap2" -> HistoryEventName.SWAP_FT_TO_VET
            "VET_FT_Swap" -> HistoryEventName.SWAP_VET_TO_FT
            "Token_FTSwap" -> HistoryEventName.SWAP_FT_TO_FT
            "MAAS_SALE" -> HistoryEventName.NFT_SALE
            "WOV_Action_Executed_Sale" -> HistoryEventName.NFT_SALE
            "WOV_Custodial_VET_Sale" -> HistoryEventName.NFT_SALE
            "WOV_Custodial_WOV_Sale" -> HistoryEventName.NFT_SALE
            "WOV_Non_Custodial_Sale" -> HistoryEventName.NFT_SALE
            "WOV_Offer_Accepted_Sale" -> HistoryEventName.NFT_SALE
            "STARGATE_STAKE" -> HistoryEventName.STARGATE_STAKE
            "STARGATE_UNSTAKE" -> HistoryEventName.STARGATE_UNSTAKE
            "STARGATE_CLAIM_REWARDS_BASE_LEGACY" ->
                HistoryEventName.STARGATE_CLAIM_REWARDS_BASE_LEGACY
            "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY" ->
                HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY
            "STARGATE_UNDELEGATE_LEGACY" -> HistoryEventName.STARGATE_UNDELEGATE_LEGACY
            "VeVote_VoteCast" -> HistoryEventName.VEVOTE_VOTE_CAST
            "STARGATE_DELEGATE_REQUEST" -> HistoryEventName.STARGATE_DELEGATE_REQUEST
            "STARGATE_DELEGATE_LEGACY" -> HistoryEventName.STARGATE_DELEGATE_LEGACY
            "STARGATE_DELEGATION_EXIT_REQUEST" -> HistoryEventName.STARGATE_DELEGATE_EXIT_REQUEST
            "STARGATE_CLAIM_REWARDS" -> HistoryEventName.STARGATE_CLAIM_REWARDS
            "STARGATE_BOOST" -> HistoryEventName.STARGATE_BOOST
            "STARGATE_MANAGER_ADDED" -> HistoryEventName.STARGATE_MANAGER_ADDED
            "STARGATE_MANAGER_REMOVED" -> HistoryEventName.STARGATE_MANAGER_REMOVED
            else -> null // Other events will not be labeled
        }

    fun determineDelegationEventType(
        delegationStatus: Status,
        forcedExit: Boolean,
    ): HistoryEventName? =
        if (delegationStatus == Status.ACTIVE) {
            HistoryEventName.STARGATE_DELEGATE_ACTIVE
        } else if (forcedExit && delegationStatus == Status.EXITED) {
            HistoryEventName.STARGATE_DELEGATION_EXITED_VALIDATOR
        } else if (delegationStatus == Status.EXITED) {
            HistoryEventName.STARGATE_DELEGATION_EXITED
        } else {
            null
        }

    fun determineValidatorEventType(params: AbiEventParameters): ValidatorAction? =
        when (params.getEventType()) {
            "DelegationAdded" -> ValidatorAction.DELEGATION_APPLIED
            "DelegationInitiated" -> ValidatorAction.DELEGATION_INITIATED
            "DelegationWithdrawn" -> ValidatorAction.DELEGATION_REMOVED
            "DelegationExitRequested" -> ValidatorAction.DELEGATION_EXIT_REQUESTED
            "Transfer" -> ValidatorAction.DELEGATION_EXIT_REQUESTED
            else -> null
        }

    fun determineTransferType(genericParams: AbiEventParameters): TransferEventType? =
        when (genericParams.getEventType()) {
            "Transfer" -> {
                when {
                    genericParams.params["value"] != null -> TransferEventType.FUNGIBLE_TOKEN
                    genericParams.params["tokenId"] != null -> TransferEventType.NFT
                    else -> null
                }
            }
            "TransferSingle",
            "TransferBatch" -> TransferEventType.SEMI_FUNGIBLE_TOKEN
            "VET_TRANSFER" -> TransferEventType.VET
            else -> null // Other events will not be labeled
        }

    /**
     * Groups events by blockId, then sorts the groups by blockNumber (ascending). The returned Map
     * preserves the sorted order of blocks via insertion order.
     *
     * @param events List of IndexedEvent to group.
     * @return Map where keys are BlockDetails (blockId, blockNumber, blockTimestamp) and values are
     *   lists of events in that block.
     */
    fun groupByBlock(events: List<IndexedEvent>): Map<BlockDetails, List<IndexedEvent>> {
        // First group by blockId so the canonical key is the blockId
        val byId: Map<String, List<IndexedEvent>> = events.groupBy { it.blockId }

        // Build (BlockDetails -> events) pairs. We derive number/timestamp from the first event in
        // each block.
        val entries: List<Pair<BlockDetails, List<IndexedEvent>>> =
            byId.map { (id, evs) ->
                val first = evs.first()
                BlockDetails(id, first.blockNumber, first.blockTimestamp) to evs
            }

        // Sort by blockNumber and return as a LinkedHashMap to preserve order
        return entries.sortedBy { (details, _) -> details.blockNumber }.toMap(LinkedHashMap())
    }

    /** Determine if a delegation-related event should be processed based on its type and source. */
    fun shouldProcessDelegationEvent(ev: IndexedEvent, stakerSC: String): Boolean =
        when (ev.eventType) {
            // must come from stakerSC
            "ValidatorExitRequested" -> ev.address.equals(stakerSC, ignoreCase = true)

            // must NOT come from stakerSC
            "DelegationInitiated",
            "DelegationExitRequested",
            "DelegationWithdrawn",
            "DelegationRewardsClaimed",
            "Transfer" -> !ev.address.equals(stakerSC, ignoreCase = true)

            else -> false
        }
}
