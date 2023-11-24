package org.vechain.indexer

import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockIdentifier

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

    override fun getLastSyncedBlock(): BlockIdentifier? {
        repository.getLatestRecord()?.let {
            return BlockIdentifier(number = it.blockNumber, id = it.blockId)
        }
        return BlockIdentifier(number = startBlock)
    }
}
