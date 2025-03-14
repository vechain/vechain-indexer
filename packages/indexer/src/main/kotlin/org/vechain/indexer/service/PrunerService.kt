package org.vechain.indexer.service

import org.slf4j.LoggerFactory
import org.vechain.indexer.Status
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument

class PrunerService<T : VersionedDocument, S : Archive<T>>(
    private val archiveService: ArchiveService<T, S>,
    private val prunerRemovalChunkSize: Int,
) {
    private val logger = LoggerFactory.getLogger(PrunerService::class.java)

    fun runPruner(
        name: String,
        currentBlockNumber: Long,
        status: Status,
    ) {
        if (status != Status.FULLY_SYNCED) {
            logger.info("Skipping pruner for $name as not fully synced")
            return
        }

        val prunerEndBlock = currentBlockNumber - 10_000
        if (prunerEndBlock <= 0) {
            logger.info("Skipping pruner for $name as not enough blocks to prune")
            return
        }

        val records = archiveService.findRecordsToPrune(prunerEndBlock)
        if (records.isEmpty()) {
            logger.info("No records to prune for $name")
            return
        }

        logger.info(
            "Pruning ${records.size} records for $name (in chunks of $prunerRemovalChunkSize)"
        )
        records.chunked(prunerRemovalChunkSize).forEach { chunk -> archiveService.removeAll(chunk) }
    }
}
