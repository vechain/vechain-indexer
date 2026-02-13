package org.vechain.indexer.pruner

import org.slf4j.LoggerFactory
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.ArchiveService

open class PrunerService<T : VersionedDocument>(
    private val archiveService: ArchiveService<T>,
    private val prunerRemovalChunkSize: Int,
) : TargetedPruner<T> {
    private val logger = LoggerFactory.getLogger(PrunerService::class.java)
    private val targetObjectName = archiveService.clazz.simpleName ?: "Unknown"

    override fun run(currentBlockNumber: Long) = run(currentBlockNumber, null)

    override fun run(currentBlockNumber: Long, idsToPrune: List<String>?) {
        val prunerEndBlock = currentBlockNumber - 10_000
        if (prunerEndBlock <= 0) {
            logger.debug("Skipping pruner for $targetObjectName, as not enough blocks to prune")
            return
        }

        logger.debug("Pruning started for $targetObjectName")
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
                        "Pruning progress for $targetObjectName: processed $processed records"
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
                logger.debug("Pruning progress for $targetObjectName: processed $processed records")
            }

            logger.debug("Pruning complete for $targetObjectName. Removed $processed records")
        }
    }
}
