package org.vechain.indexer.model.stargate

import com.fasterxml.jackson.annotation.JsonIgnore
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.model.IndexedDocument

@Document(collection = "stargate_total_nft_holders_by_block")
data class NftHoldersByBlock
@ConstructorBinding
constructor(
    @JsonIgnore override val blockId: String,
    @JsonIgnore @Id override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val total: Long,
    val byLevel: Map<Int, Long>,
) : IndexedDocument
