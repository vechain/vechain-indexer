package org.vechain.indexer.model.vevote

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.model.IndexedDocument

@Document(collection = "vevote_proposal_results")
data class VeVoteProposalResults
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val proposalId: String,
    val support: Support,
    val totalWeight: BigDecimal,
    val totalVoters: Int,
) : IndexedDocument
