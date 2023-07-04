package org.vechain.indexer

import org.vechain.indexer.repository.BaseIndexedRepository

abstract class VeWorldIndexer(
    private val repo: BaseIndexedRepository<*>,
    private val startBlock: Long = 0L,
    thorUrl: String,
    syncLoggerInterval: Long = 1000L,
) : Indexer(thorUrl, startBlock, syncLoggerInterval) {

    override fun getLastSyncedBlockNumber(): Long {
        repo.getLatestRecord()?.let {
            return it.blockNumber
        }
        return startBlock
    }
}
