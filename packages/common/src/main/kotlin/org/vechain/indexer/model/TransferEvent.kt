package org.vechain.indexer.model

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "transfer_events")
@CompoundIndex(name = "transfer_block_number_idx", def = "{'blockNumber': -1}")
data class TransferEvent @ConstructorBinding constructor(
    @Id
    val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    val txId: String,
    @Indexed
    val from: String,
    @Indexed
    val to: String,
    val value: String,
    @Indexed
    val tokenAddress: String?,
    val isVetTransfer: Boolean,
    val topics: List<String>,
) : IndexedDocument {
    val isNFTTransfer: Boolean
        get() = topics.size == 4

    val isFungibleTransfer: Boolean
        get() = topics.size == 3
}