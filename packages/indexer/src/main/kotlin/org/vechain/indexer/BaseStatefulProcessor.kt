package org.vechain.indexer

import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService

abstract class BaseStatefulProcessor(
    repository: BaseIndexedRepository<*, *>,
    private val archiveService: ArchiveService<*>,
    indexerName: String,
    checkpointService: CheckpointService,
    collectionName: String,
) : BaseProcessor(repository, indexerName, checkpointService, collectionName) {
    override fun rollback(blockNumber: Long) {
        checkpointService.deleteCheckpoint(collectionName)
        archiveService.rollback(blockNumber)
    }
}
