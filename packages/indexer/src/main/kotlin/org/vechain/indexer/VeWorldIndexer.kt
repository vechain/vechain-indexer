package org.vechain.indexer

import org.vechain.indexer.repository.BaseIndexedRepository

abstract class VeWorldIndexer(
    private val repo: BaseIndexedRepository<*>,
    private val startBlock: Long = 0L,
    thorUrl: String
) : Indexer(thorUrl, startBlock) {

    override fun getLastSyncedBlockNumber(): Long {
        repo.getLatestRecord()?.let {
            return it.blockNumber
        }
        return startBlock
    }
}
