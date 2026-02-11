package org.vechain.indexer.amn

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames

@Document(collection = IndexerNames.AUTHORITY_NODE.COLLECTION)
data class AmnEndorser(
    @Id val nodeMaster: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockTimestamp: Long,
    val endorser: String? = null,
) : IndexedDocument
