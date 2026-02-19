package org.vechain.indexer

import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

abstract class BaseStatefulProcessor(
    repository: BaseIndexedRepository<*, *>,
    private val archiveService: ArchiveService<*>,
    indexerName: String,
    checkpointService: CheckpointService,
    collectionName: String,
    processorMetrics: ProcessorMetrics,
) : BaseProcessor(repository, indexerName, checkpointService, collectionName, processorMetrics) {
    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        checkpointService.saveCheckpoint(collectionName, blockNumber - 1)
        archiveService.rollback(blockNumber)
    }
}
