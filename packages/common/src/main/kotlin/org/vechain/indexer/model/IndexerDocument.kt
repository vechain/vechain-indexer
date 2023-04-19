package org.vechain.indexer.model

/**
 * Common interface to all mongo documents.
 */
interface IndexerDocument {
    //To determine if a re-org has happened
    val blockId: String

    //To find the starting point on restarts
    val blockNumber: Long
}