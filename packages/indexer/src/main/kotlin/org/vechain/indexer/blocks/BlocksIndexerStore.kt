package org.vechain.indexer.blocks

import org.vechain.indexer.IndexerStore
import org.vechain.indexer.thor.model.BlockIdentifier

/** [IndexerStore] over the blocks tables: resume from the newest block row, roll back by delete. */
class BlocksIndexerStore(private val repository: BlocksWriteRepository) : IndexerStore {
    override fun lastSynced(): BlockIdentifier? = repository.lastSynced()

    override fun rollbackFrom(blockNumber: Long) = repository.rollbackFrom(blockNumber)
}
