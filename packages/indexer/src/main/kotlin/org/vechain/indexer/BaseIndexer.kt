package org.vechain.indexer

import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockIdentifier

abstract class BaseIndexer(
    private val repository: BaseIndexedRepository<*>,
    startBlock: Long = 0L,
    thorClient: ThorClient,
    syncLogInterval: Long = 1000L,
    abiManager: AbiManager? = null,
    businessEventManager: BusinessEventManager? = null,
) :
    BlockIndexer(
        thorClient = thorClient,
        startBlock = startBlock,
        syncLogInterval,
        abiManager,
        businessEventManager,
    ) {
    override fun getLastSyncedBlock(): BlockIdentifier? {
        repository.getLatestRecord()?.let {
            return BlockIdentifier(number = it.blockNumber, id = it.blockId)
        }
        logger.info("No records found in repository, returning null")
        return null
    }
}
