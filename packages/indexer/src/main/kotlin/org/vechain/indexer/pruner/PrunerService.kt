package org.vechain.indexer.pruner

import kotlin.reflect.KClass
import org.slf4j.LoggerFactory
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.timing.WithTiming

open class PrunerService<T : VersionedDocument, S : Archive<T>>(
    klass: KClass<S>,
    private val archiveService: ArchiveService<T, S>,
    private val prunerRemovalChunkSize: Int,
) : TargetedPruner<T, S> {
    private val logger = LoggerFactory.getLogger(PrunerService::class.java)
    private val targetObjectName = klass.simpleName ?: "Unknown"

    override fun run(currentBlockNumber: Long) = run(currentBlockNumber, null)

    @WithTiming("Pruner")
    override fun run(currentBlockNumber: Long, idsToPrune: List<String>?) {
        val prunerEndBlock = currentBlockNumber - 10_000
        if (prunerEndBlock <= 0) {
            logger.info("Skipping pruner for $targetObjectName, as not enough blocks to prune")
            return
        }

        logger.debug("🧹 Pruning started for $targetObjectName")
        archiveService.findRecordsToPrune(prunerEndBlock, prunerRemovalChunkSize, idsToPrune).use {
            records ->
            var processed = 0
            var hasRecords = false
            val chunk = ArrayList<String>(prunerRemovalChunkSize)

            while (records.hasNext()) {
                hasRecords = true
                chunk.add(records.next())

                if (chunk.size == prunerRemovalChunkSize) {
                    val removed = chunk.size
                    archiveService.removeAll(chunk.toList())
                    processed += removed
                    logger.debug(
                        "🧹 Pruning progress for $targetObjectName: processed $processed records"
                    )
                    chunk.clear()
                }
            }

            if (!hasRecords) {
                logger.debug("No records to prune for $targetObjectName")
                return
            }

            if (chunk.isNotEmpty()) {
                val removed = chunk.size
                archiveService.removeAll(chunk.toList())
                processed += removed
                logger.debug(
                    "🧹 Pruning progress for $targetObjectName: processed $processed records"
                )
            }

            logger.info("✅ Pruning complete for $targetObjectName. Removed $processed records")
        }
    }
}
