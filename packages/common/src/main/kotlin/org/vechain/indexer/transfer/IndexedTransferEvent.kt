package org.vechain.indexer.transfer

import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames

enum class TransferEventType {
    VET,
    FUNGIBLE_TOKEN,
    NFT,
    SEMI_FUNGIBLE_TOKEN,
}

@Document(collection = IndexerNames.TRANSFER.COLLECTION)
data class IndexedTransferEvent
@ConstructorBinding
constructor(
    @Id val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val txId: String,
    val from: String,
    val to: String,
    val value: String,
    val tokenAddress: String?,
    val tokenId: String?,
    val topics: List<String>,
    val eventType: TransferEventType,
) : IndexedDocument
