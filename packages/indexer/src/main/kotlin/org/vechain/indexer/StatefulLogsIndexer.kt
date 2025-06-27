package org.vechain.indexer

import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.pruner.Prunable
import org.vechain.indexer.pruner.Pruner
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType

abstract class StatefulLogsIndexer<T : VersionedDocument, S : Archive<T>>(
    repository: BaseIndexedRepository<*, *>,
    startBlock: Long = 0L,
    thorClient: ThorClient,
    syncLogInterval: Long = 1000L,
    blockBatchSize: Long = 100L,
    logsType: Set<LogType>,
    abiManager: AbiManager?,
    businessEventManager: BusinessEventManager?,
    private val archiveService: ArchiveService<T, S>,
    private val pruner: Pruner<T, S>,
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
    ),
    Prunable {
    override fun rollback(blockNumber: Long) {
        archiveService.rollback(blockNumber)
    }

    override fun runPruner() {
        pruner.prune(this.currentBlockNumber, this.status)
    }
}
