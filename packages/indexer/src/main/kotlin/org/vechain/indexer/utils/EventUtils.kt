package org.vechain.indexer.utils

import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.model.history.HistoryEventName
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

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
            "STARGATE_DELEGATE" -> HistoryEventName.STARGATE_DELEGATE_ONLY
            "STARGATE_STAKE_DELEGATE" -> HistoryEventName.STARGATE_DELEGATE
            "STARGATE_STAKE" -> HistoryEventName.STARGATE_STAKE
            "STARGATE_UNSTAKE" -> HistoryEventName.STARGATE_UNSTAKE
            "STARGATE_CLAIM_REWARDS_BASE" -> HistoryEventName.STARGATE_CLAIM_REWARDS_BASE
            "STARGATE_CLAIM_REWARDS_DELEGATE" -> HistoryEventName.STARGATE_CLAIM_REWARDS_DELEGATE
            "STARGATE_UNDELEGATE" -> HistoryEventName.STARGATE_UNDELEGATE
            else -> null // Other events will not be labeled
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

    fun getChoice(choiceValue: Long): List<Int> {
        if (choiceValue < 0) {
            return emptyList()
        }
        return choiceValue.toString(2).reversed().mapIndexedNotNull { index, bit ->
            if (bit == '1') index + 1 else null
        }
    }

    fun getStargateRewards(genericParams: AbiEventParameters): String {
        if (genericParams.getAsString("value") != null) return genericParams.getAsString("value")!!

        val totalRewards =
            genericParams.getAsBigInteger("vetGeneratedVthoRewards")!! +
                genericParams.getAsBigInteger("delegationRewards")!!
        return totalRewards.toString()
    }
}
