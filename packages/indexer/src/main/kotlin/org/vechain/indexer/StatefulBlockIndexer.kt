package org.vechain.indexer

import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.pruner.Prunable
import org.vechain.indexer.pruner.Pruner
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.thor.client.ThorClient

abstract class StatefulBlockIndexer<T : VersionedDocument, S : Archive<T>>(
    repository: BaseIndexedRepository<*, *>,
    startBlock: Long = 0L,
    thorClient: ThorClient,
    syncLogInterval: Long = 1000L,
    private val archiveService: ArchiveService<T, S>,
    private val pruner: Pruner<T, S>,
) :
    BaseIndexer(
        repository = repository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
    ),
    Prunable {
    override fun rollback(blockNumber: Long) {
        archiveService.rollback(blockNumber)
    }

    override fun runPruner() {
        pruner.prune(this.currentBlockNumber, this.status)
    }
}
