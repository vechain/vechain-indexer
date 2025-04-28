package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.thor.model.Block

@Document(collection = "vevote_choice_aggregates")
data class VoteAggregate
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val proposalId: String,
    val choice: Int,
    val totalWeight: BigDecimal,
    val voteCount: Int
) : IndexedDocument {
    constructor(
        block: Block,
        proposalId: String,
        choice: Int,
        totalWeight: BigDecimal,
        voteCount: Int
    ) : this(
        id = generateVoteAggregateId(proposalId, choice),
        blockId = block.id,
        blockNumber = block.number,
        blockTimestamp = block.timestamp,
        proposalId = proposalId,
        choice = choice,
        totalWeight = totalWeight,
        voteCount = voteCount
    )
}

fun generateVoteAggregateId(proposalId: String, choice: Int): String = "$proposalId-$choice"
