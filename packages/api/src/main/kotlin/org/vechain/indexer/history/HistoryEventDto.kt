package org.vechain.indexer.history

import org.vechain.indexer.b3tr.action.SustainabilityProofV2
import org.vechain.indexer.b3tr.voting.AppVote
import org.vechain.indexer.b3tr.voting.Support

// TODO: Remove this when veworld have migrated to V2 History API
data class HistoryEventDto(
    val id: String,
    val blockId: String,
    val blockNumber: Long,
    val blockTimestamp: Long,
    val txId: String,
    val origin: String?,
    val gasPayer: String?,
    val reverted: Boolean?,
    val contractAddress: String?,
    val tokenId: String?,
    val eventName: String, // <— NOTE: String for legacy output
    val to: String?,
    val from: String?,
    val value: String?,
    val appId: String?,
    val proof: SustainabilityProofV2?,
    val roundId: String?,
    val appVotes: List<AppVote>?,
    val support: Support?,
    val votePower: String?,
    val voteWeight: String?,
    val reason: String?,
    val proposalId: String?,
    val oldLevel: String?,
    val newLevel: String?,
    val inputToken: String?,
    val outputToken: String?,
    val inputValue: String?,
    val outputValue: String?,
    val tokenAddress: String?,
    val levelId: String?,
    val owner: String?,
    val vetGeneratedVthoRewards: String?,
    val delegationRewards: String?,
    val migrated: Boolean?,
    val autorenew: Boolean?,
    val tokenIds: List<String>?,
    val validator: String?,
    val delegationId: String?,
    val periodClaimed: Long?,
    val boostedBlocks: String?,
) {
    companion object {
        fun fromIndexed(e: IndexedHistoryEvent, legacy: Boolean): HistoryEventDto =
            HistoryEventDto(
                id = e.id,
                blockId = e.blockId,
                blockNumber = e.blockNumber,
                blockTimestamp = e.blockTimestamp,
                txId = e.txId,
                origin = e.origin,
                gasPayer = e.gasPayer,
                reverted = e.reverted,
                contractAddress = e.contractAddress,
                tokenId = e.tokenId,
                eventName =
                    if (legacy) {
                        HistoryUtils.mapEnumToOldStringForLegacy(e.eventName)
                    } else {
                        e.eventName.name
                    },
                to = e.to,
                from = e.from,
                value = e.value,
                appId = e.appId,
                proof = e.proof,
                roundId = e.roundId,
                appVotes = e.appVotes,
                support = e.support,
                votePower = e.votePower,
                voteWeight = e.voteWeight,
                reason = e.reason,
                proposalId = e.proposalId,
                oldLevel = e.oldLevel,
                newLevel = e.newLevel,
                inputToken = e.inputToken,
                outputToken = e.outputToken,
                inputValue = e.inputValue,
                outputValue = e.outputValue,
                tokenAddress = e.tokenAddress,
                levelId = e.levelId,
                owner = e.owner,
                vetGeneratedVthoRewards = e.vetGeneratedVthoRewards,
                delegationRewards = e.delegationRewards,
                migrated = e.migrated,
                autorenew = e.autorenew,
                tokenIds = e.tokenIds,
                validator = e.validator,
                delegationId = e.delegationId,
                periodClaimed = e.periodClaimed,
                boostedBlocks = e.boostedBlocks,
            )
    }
}
