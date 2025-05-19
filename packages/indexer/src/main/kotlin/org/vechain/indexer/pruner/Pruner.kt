package org.vechain.indexer.pruner

import kotlin.reflect.KClass
import org.slf4j.LoggerFactory
import org.vechain.indexer.Status
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.service.ArchiveService

class Pruner<T : VersionedDocument, S : Archive<T>>(
    klass: KClass<S>,
    private val archiveService: ArchiveService<T, S>,
    private val prunerRemovalChunkSize: Int,
) {
    private val logger = LoggerFactory.getLogger(Pruner::class.java)
    private val targetObjectName = klass.simpleName ?: "Unknown"

    fun prune(currentBlockNumber: Long, status: Status) {
        if (status != Status.FULLY_SYNCED) {
            logger.info("Skipping pruner for $targetObjectName, as not fully synced")
            return
        }

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
        records.chunked(prunerRemovalChunkSize).forEach { chunk -> archiveService.removeAll(chunk) }
    }
}
