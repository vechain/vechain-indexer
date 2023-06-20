package org.vechain.indexer

import org.vechain.indexer.repository.BaseIndexedRepo

abstract class VeWorldIndexer(
    private val repo: BaseIndexedRepo<*>,
    thorUrl: String,
) : Indexer(thorUrl) {

    override fun getLastSyncedBlockNumber(): Long {
        repo.getLatestRecord()?.let {
            return it.blockNumber
        }
        return 0
    }

    override fun rollback(blockNumber: Long) {
        repo.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
