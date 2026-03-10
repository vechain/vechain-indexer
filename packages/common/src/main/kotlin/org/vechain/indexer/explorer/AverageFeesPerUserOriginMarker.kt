package org.vechain.indexer.explorer

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames

@Document(collection = IndexerNames.AVERAGE_FEES_PER_USER_ORIGIN_MARKER.COLLECTION)
data class AverageFeesPerUserOriginMarker(
    @JsonIgnore @Id val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    @JsonIgnore val date: String,
    @JsonIgnore val origin: String,
) : IndexedDocument
