package org.vechain.indexer.model

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "authority_nodes")
data class AuthorityNode(
    @Id val nodeMaster: String,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
    val endorser: String? = null,
    val identity: String? = null,
    val active: Boolean? = null,
    val listed: Boolean? = null,
) : IndexedDocument

fun generateId(proposalId: String, reason: String): String =
    DigestUtils.sha1Hex("$proposalId-${reason.trim().lowercase()}")
