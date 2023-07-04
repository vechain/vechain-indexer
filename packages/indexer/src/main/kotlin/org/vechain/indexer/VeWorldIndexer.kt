package org.vechain.indexer

import org.vechain.indexer.repository.BaseIndexedRepository

abstract class VeWorldIndexer(
    private val repository: BaseIndexedRepository<*>,
    private val startBlock: Long = 0L,
    thorClient: ThorClient
) : Indexer(thorClient = thorClient, startBlock = startBlock) {

    override fun getLastSyncedBlockNumber(): Long {
        repository.getLatestRecord()?.let {
            return it.blockNumber
        }
        return startBlock
    }
}
