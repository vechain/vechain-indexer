package org.vechain.indexer

import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService

abstract class BaseStatefulProcessor(
    repository: BaseIndexedRepository<*, *>,
    private val archiveService: ArchiveService<*, *>,
    indexerName: String,
    checkpointService: CheckpointService,
    collectionName: String,
) : BaseProcessor(repository, indexerName, checkpointService, collectionName) {
    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        checkpointService.saveCheckpoint(collectionName, blockNumber - 1)
        archiveService.rollback(blockNumber)
    }
}
