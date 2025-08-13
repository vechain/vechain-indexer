package org.vechain.indexer.history

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.b3tr.AppVote
import org.vechain.indexer.b3tr.ProposalSupport

@Document(collection = "history_events")
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    val reverted: Boolean? = null,
    val contractAddress: String? = null,
    val tokenId: String? = null,
    val eventName: HistoryEventName,
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
    val tokenAddress: String? = null,
    val levelId: String? = null,
    val owner: String? = null,
    val vetGeneratedVthoRewards: String? = null,
    val delegationRewards: String? = null,
    val migrated: Boolean? = null,
    val autorenew: Boolean? = null,
    val tokenIds: List<String>? = null,
    val validator: String? = null,
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
