package org.vechain.indexer.b3tr.navigator

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames

@Document(collection = IndexerNames.NAVIGATOR_FEE.COLLECTION)
data class NavigatorFee
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    override val blockTimestamp: Long,
    val txId: String,
    val navigator: String,
    val eventType: String,
    val roundId: String,
    val amount: String,
    val citizen: String?,
) : IndexedDocument
