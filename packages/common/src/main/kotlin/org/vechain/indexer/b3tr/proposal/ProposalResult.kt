package org.vechain.indexer.b3tr.proposal

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.b3tr.voting.Support

@Document(collection = IndexerNames.PROPOSAL_RESULT.COLLECTION)
data class ProposalResult
@ConstructorBinding
constructor(
    @Id val proposalId: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val createdAtBlockNumber: Long,
    val startRoundId: Int,
    val state: ProposalState,
    val results: VoteResults?,
    val description: String,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = proposalId

    // Convert to deprecated format
    fun toDeprecated(): List<ProposalResultDeprecated> =
        results?.let {
            listOf(
                ProposalResultDeprecated(
                    proposalId = proposalId,
                    support = Support.FOR,
                    voters = it.forResult.voters,
                    totalWeight = it.forResult.totalWeight,
                    totalPower = it.forResult.totalPower,
                ),
                ProposalResultDeprecated(
                    proposalId = proposalId,
                    support = Support.AGAINST,
                    voters = it.againstResult.voters,
                    totalWeight = it.againstResult.totalWeight,
                    totalPower = it.againstResult.totalPower,
                ),
                ProposalResultDeprecated(
                    proposalId = proposalId,
                    support = Support.ABSTAIN,
                    voters = it.abstainResult.voters,
                    totalWeight = it.abstainResult.totalWeight,
                    totalPower = it.abstainResult.totalPower,
                ),
            )
        } ?: emptyList()
}

@Document(collection = "b3tr_proposal_result_archives")
data class ProposalResultArchive(@Id override val id: String, override val data: ProposalResult) :
    Archive<ProposalResult>

data class VoteResults(val forResult: Result, val againstResult: Result, val abstainResult: Result)

data class Result(val voters: Long, val totalWeight: BigInteger, val totalPower: BigInteger)

data class ProposalResultDeprecated(
    val proposalId: String,
    val support: Support,
    val voters: Long,
    val totalWeight: BigInteger,
    val totalPower: BigInteger,
)

// ProposalState enum to store the state of a proposal
// Serialized as string in both MongoDB and JSON API responses
enum class ProposalState {
    Pending,
    Active,
    Canceled,
    Defeated,
    Succeeded,
    Queued,
    Executed,
    DepositNotMet,
    InDevelopment,
    Completed;

    companion object {
        fun getOrdinal(state: ProposalState): Int = state.ordinal

        fun fromOrdinal(ordinal: Int): ProposalState? = entries.getOrNull(ordinal)

        val finalizedStates: List<ProposalState> =
            listOf(Executed, DepositNotMet, Canceled, Defeated, Completed)
        val nonFinalizedStates: List<ProposalState> =
            listOf(Pending, Active, Succeeded, Queued, InDevelopment)
    }
}
