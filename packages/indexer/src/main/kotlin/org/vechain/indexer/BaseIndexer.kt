package org.vechain.indexer

import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockIdentifier

abstract class BaseIndexer(
    private val repository: BaseIndexedRepository<*>,
    startBlock: Long = 0L,
    thorClient: ThorClient,
    syncLogInterval: Long = 1000L,
) : Indexer(thorClient = thorClient, startBlock = startBlock, syncLogInterval) {

    override fun getLastSyncedBlock(): BlockIdentifier? {
        repository.getLatestRecord()?.let {
            return BlockIdentifier(number = it.blockNumber, id = it.blockId)
        }
        return null
    }
}
