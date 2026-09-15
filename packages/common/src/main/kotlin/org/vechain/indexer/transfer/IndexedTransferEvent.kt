package org.vechain.indexer.transfer

import com.fasterxml.jackson.annotation.JsonIgnore
import org.vechain.indexer.IndexedDocument

enum class TransferEventType {
    VET,
    FUNGIBLE_TOKEN,
    NFT,
    SEMI_FUNGIBLE_TOKEN,
}

data class IndexedTransferEvent(
    val id: String,
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    @get:JsonIgnore val transferIndex: Long = 0L,
    val txId: String,
    val from: String,
    val to: String,
    val value: String,
    val tokenAddress: String?,
    val tokenId: String?,
    val topics: List<String>,
    val eventType: TransferEventType,
) : IndexedDocument
