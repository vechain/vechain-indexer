package org.vechain.indexer.pruner

import kotlin.reflect.KClass
import org.slf4j.LoggerFactory
import org.vechain.indexer.Pruner
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.timing.WithTiming

open class PrunerService<T : VersionedDocument, S : Archive<T>>(
    klass: KClass<S>,
    private val archiveService: ArchiveService<T, S>,
    private val prunerRemovalChunkSize: Int,
) : Pruner {
    private val logger = LoggerFactory.getLogger(PrunerService::class.java)
    private val targetObjectName = klass.simpleName ?: "Unknown"

    @WithTiming("Pruner.run")
    override fun run(currentBlockNumber: Long) {
        val prunerEndBlock = currentBlockNumber - 10_000
        if (prunerEndBlock <= 0) {
            logger.info("Skipping pruner for $targetObjectName, as not enough blocks to prune")
            return
        }

        logger.info("🧹 Pruning started for $targetObjectName")

        val records = archiveService.findRecordsToPrune(prunerEndBlock)
        if (records.isEmpty()) {
            logger.info("No records to prune for $targetObjectName")
            return
        }

        records.chunked(prunerRemovalChunkSize).withIndex().forEach { (idx, chunk) ->
            logger.debug(
                "🧹 Pruning progress for $targetObjectName: ${
                        progressPercentage(
                            (idx * prunerRemovalChunkSize) + chunk.size,
                            records.size,
                        )
                    }%"
            )
            archiveService.removeAll(chunk)
        }

        logger.info("✅ Pruning complete for $targetObjectName. Removed ${records.size} records")
    }

    private fun progressPercentage(processed: Int, total: Int): Int =
        if (total == 0) 0 else (processed * 100) / total
}
