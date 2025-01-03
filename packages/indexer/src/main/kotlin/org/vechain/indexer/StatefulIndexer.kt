package org.vechain.indexer

import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.thor.client.ThorClient

abstract class StatefulIndexer<T : VersionedDocument, S : Archive<T>>(
    repository: BaseIndexedRepository<*>,
    startBlock: Long = 0L,
    thorClient: ThorClient,
    syncLogInterval: Long = 1000L,
    private val prunerRemovalChunkSize: Int,
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

    fun runPruner() {
        // Only run the pruner if the indexer is synced
        if (this.status != Status.FULLY_SYNCED) {
            logger.info("Skipping pruner for ${this.name} as not fully synced")
            return
        }

        // Assume the block 10,000 blocks ago is finalised and a safe point to prune to
        val prunerEndBlock = this.currentBlockNumber - 10_000
        if (prunerEndBlock <= 0) {
            logger.info("Skipping pruner for ${this.name} as not enough blocks to prune")
            return
        }

        // Get the records to prune
        val records = archiveService.findRecordsToPrune(prunerEndBlock)
        if (records.isEmpty()) {
            logger.info("No records to prune for ${this.name}")
            return
        }

        // Prune the records in chunks to avoid memory issues
        logger.info(
            "Pruning ${records.size} records for ${this.name} (in chunks of ${prunerRemovalChunkSize})"
        )
        records.chunked(prunerRemovalChunkSize).forEach { chunk -> archiveService.removeAll(chunk) }
    }
}
