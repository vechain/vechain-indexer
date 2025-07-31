package org.vechain.indexer

import org.vechain.indexer.archive.ArchiveService

abstract class BaseStatefulProcessor(
    repository: BaseIndexedRepository<*, *>,
    private val archiveService: ArchiveService<*, *>,
) : BaseProcessor(repository) {
    override fun rollback(blockNumber: Long) = archiveService.rollback(blockNumber)
}
