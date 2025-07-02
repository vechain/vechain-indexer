package org.vechain.indexer.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "authority_nodes")
data class AuthorityNodeEndorser(
    @Id val nodeMaster: String,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
    val endorser: String? = null,
) : IndexedDocument
