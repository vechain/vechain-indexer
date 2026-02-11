package org.vechain.indexer.vevote

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.utils.IdUtils.generateId

@Document(collection = IndexerNames.VEVOTE_RESULT.COLLECTION)
data class VeVoteProposalResult
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val proposalId: String,
    val support: Support,
    val totalWeight: BigDecimal,
    val totalVoters: Int,
) : VersionedDocument {
    constructor(
        version: Int,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        proposalId: String,
        support: Support,
        totalWeight: BigDecimal,
        totalVoters: Int,
    ) : this(
        id = generateId(proposalId, support.name),
        version = version,
        blockId = blockId,
        blockNumber = blockNumber,
        blockTimestamp = blockTimestamp,
        proposalId = proposalId,
        support = support,
        totalWeight = totalWeight,
        totalVoters = totalVoters,
    )

    @JsonIgnore override fun getDocumentId(): String = id
}

@Document(collection = "vevote_proposal_results_archives")
data class VeVoteProposalResultArchive(
    @Id override val id: String,
    override val data: VeVoteProposalResult,
) : Archive<VeVoteProposalResult>
