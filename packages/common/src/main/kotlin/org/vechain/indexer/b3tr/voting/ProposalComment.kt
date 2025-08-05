package org.vechain.indexer.b3tr.voting

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.thor.model.Block

@Document(collection = "proposal_comments")
data class ProposalComment
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val voter: String,
    val proposalId: String,
    val support: Support,
    val weight: BigInteger,
    val power: BigInteger,
    val reason: String,
) : IndexedDocument {
    constructor(
        block: Block,
        voter: String,
        proposalId: String,
        support: Support,
        weight: BigInteger,
        power: BigInteger,
        reason: String,
    ) : this(
        id = generateId(proposalId, reason),
        blockId = block.id,
        blockNumber = block.number,
        blockTimestamp = block.timestamp,
        voter = voter,
        proposalId = proposalId,
        support = support,
        weight = weight,
        power = power,
        reason = reason,
    )
}

fun generateId(proposalId: String, reason: String): String =
    DigestUtils.sha1Hex("$proposalId-${reason.trim().lowercase()}")
