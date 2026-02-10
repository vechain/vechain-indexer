package org.vechain.indexer

import org.vechain.indexer.archive.ArchiveService

abstract class BaseStatefulProcessor(
    repository: BaseIndexedRepository<*, *>,
    private val archiveService: ArchiveService<*, *>,
    indexerName: String,
) : BaseProcessor(repository, indexerName) {
    override fun rollback(blockNumber: Long) = archiveService.rollback(blockNumber)
}
