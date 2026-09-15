package org.vechain.indexer.safe

/** A Safe the canonical factory deployed; the row is what makes an address a real Safe. */
data class SafeProxy(
    val address: String,
    val singleton: String,
    val createdBlock: Long,
    val createdTimestamp: Long,
    val vechainTxId: String,
    val blockId: String,
    val blockNumber: Long,
    val blockTimestamp: Long,
)
