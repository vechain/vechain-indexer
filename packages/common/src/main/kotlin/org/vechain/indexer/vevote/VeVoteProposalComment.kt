package org.vechain.indexer.vevote

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument

@Document(collection = "vevote_proposal_comments")
data class VeVoteProposalComment
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
    val reason: String,
) : IndexedDocument

fun generateId(proposalId: String, reason: String): String =
    DigestUtils.sha1Hex("$proposalId-${reason.trim().lowercase()}")
