package org.vechain.indexer

import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.PrunerService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType

abstract class StatefulLogsIndexer<T : VersionedDocument, S : Archive<T>>(
    repository: BaseIndexedRepository<*>,
    startBlock: Long = 0L,
    thorClient: ThorClient,
    syncLogInterval: Long = 1000L,
    blockBatchSize: Long = 1000,
    logsType: Set<LogType>,
    abiManager: AbiManager?,
    businessEventManager: BusinessEventManager?,
    private val archiveService: ArchiveService<T, S>,
    private val prunerService: PrunerService<T, S>,
) :
    BaseLogIndexer(
        repository = repository,
        startBlock = startBlock,
        thorClient = thorClient,
        blockBatchSize = blockBatchSize,
        syncLogInterval = syncLogInterval,
        logsType = logsType,
        abiManager = abiManager,
        businessEventManager = businessEventManager,
    ) {
    override fun rollback(blockNumber: Long) {
        archiveService.rollback(blockNumber)
    }

    fun runPruner() {
        prunerService.runPruner(this.name, this.currentBlockNumber, this.status)
    }
}
