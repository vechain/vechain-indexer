package org.vechain.indexer.model

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "authority_nodes")
data class IndexedAuthorityNode(
    @Id val nodeMaster: String,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
) : IndexedDocument

fun generateId(proposalId: String, reason: String): String =
    DigestUtils.sha1Hex("$proposalId-${reason.trim().lowercase()}")
