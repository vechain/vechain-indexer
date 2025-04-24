package org.vechain.indexer

import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.BlockIdentifier

abstract class BaseLogIndexer(
    private val repository: BaseIndexedRepository<*>,
    startBlock: Long = 0L,
    thorClient: ThorClient,
    syncLogInterval: Long = 1000L,
    logsType: Set<LogType> = setOf(LogType.EVENT),
    blockBatchSize: Long = 1000,
    abiManager: AbiManager? = null,
    businessEventManager: BusinessEventManager? = null,
) :
    LogsIndexer(
        thorClient = thorClient,
        startBlock = startBlock,
        syncLogInterval,
        logsType = logsType,
        logFetchLimit = 1000,
        blockBatchSize = blockBatchSize,
        abiManager = abiManager,
        businessEventManager = businessEventManager,
    ) {
    override fun getLastSyncedBlock(): BlockIdentifier? {
        repository.getLatestRecord()?.let {
            return BlockIdentifier(number = it.blockNumber, id = it.blockId)
        }
        logger.info("No records found in repository, returning null")
        return null
    }
}
