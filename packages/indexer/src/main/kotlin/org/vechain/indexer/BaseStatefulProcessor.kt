package org.vechain.indexer

import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.version.IndexerVersionService

abstract class BaseStatefulProcessor(
    repository: BaseIndexedRepository<*, *>,
    private val archiveService: ArchiveService<*, *>,
    indexerVersionService: IndexerVersionService,
    indexerName: String,
) : BaseProcessor(repository, indexerVersionService, indexerName) {
    override fun rollback(blockNumber: Long) = archiveService.rollback(blockNumber)
}
