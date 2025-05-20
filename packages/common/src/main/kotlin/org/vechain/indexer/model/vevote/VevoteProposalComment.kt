package org.vechain.indexer.model.vevote

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.model.IndexedDocument
import org.vechain.indexer.thor.model.Block

@Document(collection = "vevote_proposal_comments")
data class VevoteProposalComment
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val voter: String,
    val proposalId: String,
    val choices: List<Int>,
    val weight: BigInteger,
    val reason: String,
) : IndexedDocument {
    constructor(
        block: Block,
        voter: String,
        proposalId: String,
        choices: List<Int>,
        weight: BigInteger,
        reason: String,
    ) : this(
        id = generateId(proposalId, reason),
        blockId = block.id,
        blockNumber = block.number,
        blockTimestamp = block.timestamp,
        voter = voter,
        proposalId = proposalId,
        choices = choices,
        weight = weight,
        reason = reason,
    )
}

fun generateId(proposalId: String, reason: String): String =
    DigestUtils.sha1Hex("$proposalId-${reason.trim().lowercase()}")
