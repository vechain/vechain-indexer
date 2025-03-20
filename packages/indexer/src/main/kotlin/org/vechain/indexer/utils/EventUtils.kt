package org.vechain.indexer.utils

import org.vechain.indexer.event.model.generic.GenericEventParameters
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.model.history.HistoryEventName

object EventUtils {
    fun determineEventType(params: GenericEventParameters): HistoryEventName? =
        when (params.getEventType()) {
            "B3TR_Vot3ToB3trSwap" -> HistoryEventName.B3TR_SWAP_VOT3_TO_B3TR
            "B3TR_B3trToVot3Swap" -> HistoryEventName.B3TR_SWAP_B3TR_TO_VOT3
            "B3TR_ProposalDeposit" -> HistoryEventName.B3TR_PROPOSAL_SUPPORT
            "B3TR_ClaimReward" -> HistoryEventName.B3TR_CLAIM_REWARD
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
            "TransferBatch", -> HistoryEventName.TRANSFER_SF
            "VET_TRANSFER" -> HistoryEventName.TRANSFER_VET
            "FT_VET_Swap" -> HistoryEventName.SWAP_FT_TO_VET
            "VET_FT_Swap" -> HistoryEventName.SWAP_VET_TO_FT
            "Token_FTSwap" -> HistoryEventName.SWAP_FT_TO_FT
            "NFT_SALE" -> HistoryEventName.NFT_SALE
            else -> null // Other events will not be labeled
        }

    fun determineTransferType(genericParams: GenericEventParameters): TransferEventType? =
        when (genericParams.getEventType()) {
            "Transfer" -> {
                when {
                    genericParams.params["value"] != null -> TransferEventType.FUNGIBLE_TOKEN
                    genericParams.params["tokenId"] != null -> TransferEventType.NFT
                    else -> null
                }
            }
            "TransferSingle",
            "TransferBatch", -> TransferEventType.SEMI_FUNGIBLE_TOKEN
            "VET_TRANSFER" -> TransferEventType.VET
            else -> null // Other events will not be labeled
        }
}
