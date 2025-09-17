package org.vechain.indexer.pruner

import kotlin.reflect.KClass
import org.slf4j.LoggerFactory
import org.vechain.indexer.Pruner
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.archive.ArchiveService

class PrunerService<T : VersionedDocument, S : Archive<T>>(
    klass: KClass<S>,
    private val archiveService: ArchiveService<T, S>,
    private val prunerRemovalChunkSize: Int,
) : Pruner {
    private val logger = LoggerFactory.getLogger(PrunerService::class.java)
    private val targetObjectName = klass.simpleName ?: "Unknown"

    override fun run(currentBlockNumber: Long) {
        val prunerEndBlock = currentBlockNumber - 10_000
        if (prunerEndBlock <= 0) {
            logger.info("Skipping pruner for $targetObjectName, as not enough blocks to prune")
            return
        }

        val records = archiveService.findRecordsToPrune(prunerEndBlock)
        if (records.isEmpty()) {
            logger.info("No records to prune for $targetObjectName")
            return
        }

        logger.info(
            "Pruning ${records.size} records for $targetObjectName (in chunks of $prunerRemovalChunkSize)"
        )
        records.chunked(prunerRemovalChunkSize).forEachIndexed { index, chunk ->
            logger.debug(
                "Processing chunk ${index + 1}/${records.size / prunerRemovalChunkSize + 1} with ${chunk.size} records for $targetObjectName"
            )
            archiveService.removeAll(chunk)
        }

        logger.info("Pruning completed for $targetObjectName")
    }
}
