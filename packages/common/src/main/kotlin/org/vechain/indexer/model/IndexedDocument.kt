package org.vechain.indexer.model

/** Common interface to all mongo documents representing blockchain data. */
interface IndexedDocument {
    // To determine if a re-organization has happened
    val blockId: String

    // To find the starting point on restarts
    val blockNumber: Long

    // To access timestamp of operations contained in block
    val blockTimestamp: Long
}
