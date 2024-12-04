package org.vechain.indexer

import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.thor.client.ThorClient

abstract class StatefulIndexer<T : VersionedDocument, S : Archive<T>>(
    repository: BaseIndexedRepository<*>,
    startBlock: Long,
    thorClient: ThorClient,
    syncLogInterval: Long,
    private val prunerEnabled: Boolean,
    private val prunerInterval: Long,
    private val archiveService: ArchiveService<T, S>
) :
    BaseIndexer(
        repository = repository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval
    ) {

    override fun rollback(blockNumber: Long) {
        archiveService.rollback(blockNumber)
    }

    fun runPruner(blockNumber: Long) {
        if (prunerEnabled && blockNumber % prunerInterval == 0L) {
            prune(blockNumber)
        }
    }

    fun prune(blockNumber: Long) {
        archiveService.prune(blockNumber)
    }
}
