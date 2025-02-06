package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

enum class HistoryEventName {
    B3TR_SWAP_VOT3_TO_B3TR,
    B3TR_SWAP_B3TR_TO_VOT3,
    B3TR_PROPOSAL_SUPPORT,
    B3TR_CLAIM_REWARD,
    B3TR_UPGRADE_GM,
    B3TR_ACTION,
    B3TR_PROPOSAL_VOTE,
    B3TR_XALLOCATION_VOTE,
    TRANSFER_VET,
    TRANSFER_FT,
    TRANSFER_NFT,
    TRANSFER_SF,
    SWAP_VET_TO_FT,
    SWAP_FT_TO_VET,
    SWAP_FT_TO_FT,
}

enum class HistoryEventType {
    B3TR,
    TRANSFER,
    SWAP,
    GENERIC_TX,
}

enum class ProposalSupport(val value: Int) {
    AGAINST(0),
    FOR(1),
    ABSTAIN(2);

    companion object {
        fun fromValue(value: Int?): ProposalSupport? {
            return values().find { it.value == value }
        }
    }
}

/** Data class representing an App ID and its corresponding vote weight. */
data class AppVote(val appId: String, val voteWeight: String)

@Document(collection = "history_events")
data class IndexedHistoryEvent
@ConstructorBinding
constructor(
    @Id val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val txId: String,
    val origin: String? = null,
    val gasPayer: String? = null,
    val contractAddress: String? = null,
    val tokenId: String? = null,
    val topics: List<String>? = null,
    val data: String? = null,
    val eventName: HistoryEventName? = null,
    val eventType: HistoryEventType,
    val to: String? = null,
    val from: String? = null,
    val value: String? = null,
    val appId: String? = null,
    val proof: String? = null,
    val roundId: String? = null,
    val appVotes: List<AppVote>? = null,
    val support: ProposalSupport? = null,
    val votePower: String? = null,
    val voteWeight: String? = null,
    val reason: String? = null,
    val proposalId: String? = null,
    val oldLevel: String? = null,
    val newLevel: String? = null,
    val inputToken: String? = null,
    val outputToken: String? = null,
    val inputValue: String? = null,
    val outputValue: String? = null,
) : IndexedDocument {

    companion object {
        fun getAppVotes(appIds: Any?, voteWeights: Any?): List<AppVote>? {
            // Ensure both are non-null and cast to List<String>
            val appIdsList = appIds as? List<*> ?: return null
            val voteWeightsList = voteWeights as? List<*> ?: return null

            // Map indexed values safely
            return appIdsList.mapIndexedNotNull { index, appId ->
                voteWeightsList.getOrNull(index)?.let { voteWeight ->
                    AppVote(appId.toString(), voteWeight.toString())
                }
            }
        }
    }
}
