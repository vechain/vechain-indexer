package org.vechain.indexer.vevote

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigDecimal
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.thor.model.Views

@Document(collection = "vevote_proposal_results")
data class VeVoteProposalResults
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    @JsonView(Views.Internal::class) override val version: Int,
    val proposalId: String,
    val support: Support,
    val totalWeight: BigDecimal,
    val totalVoters: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}

@Document("vevote_proposal_results_archives")
@JsonView(Views.Public::class)
data class VeVoteProposalResultsArchive
@ConstructorBinding
constructor(@Id override val id: String, override val data: VeVoteProposalResults) :
    Archive<VeVoteProposalResults>
