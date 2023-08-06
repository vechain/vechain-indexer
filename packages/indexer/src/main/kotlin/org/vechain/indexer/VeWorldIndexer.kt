package org.vechain.indexer

import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.thor.client.ThorClient

abstract class VeWorldIndexer(
    private val repository: BaseIndexedRepository<*>,
    private val startBlock: Long = 0L,
    thorClient: ThorClient,
    syncLoggerInterval: Long = 1000L,
) :
    Indexer(
        thorClient = thorClient,
        startBlock = startBlock,
        syncLoggerInterval = syncLoggerInterval
    ) {

    override fun getLastSyncedBlockNumber(): Long {
        repository.getLatestRecord()?.let {
            return it.blockNumber
        }
        return startBlock
    }
}
