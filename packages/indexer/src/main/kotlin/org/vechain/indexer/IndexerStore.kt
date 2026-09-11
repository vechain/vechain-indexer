package org.vechain.indexer

import org.vechain.indexer.thor.model.BlockIdentifier

/** What a processor needs from its storage: where it stopped, and how to undo from a block on. */
interface IndexerStore {
    fun lastSynced(): BlockIdentifier?

    fun rollbackFrom(blockNumber: Long)
}
