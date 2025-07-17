package org.vechain.indexer.model

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "authority_nodes")
data class AmnEndorser(
    @Id val nodeMaster: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockTimestamp: Long,
    val endorser: String? = null,
) : IndexedDocument
