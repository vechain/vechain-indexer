package org.vechain.indexer

import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.PrunerService
import org.vechain.indexer.thor.client.ThorClient

abstract class StatefulIndexer<T : VersionedDocument, S : Archive<T>>(
    repository: BaseIndexedRepository<*>,
    startBlock: Long = 0L,
    thorClient: ThorClient,
    syncLogInterval: Long = 1000L,
    private val prunerRemovalChunkSize: Int,
    private val archiveService: ArchiveService<T, S>,
    private val prunerService: PrunerService<T, S>,
) :
    BaseIndexer(
        repository = repository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
    ) {
    override fun rollback(blockNumber: Long) {
        archiveService.rollback(blockNumber)
    }

    fun runPruner() {
        prunerService.runPruner(this.name, this.currentBlockNumber, this.status)
    }
}
