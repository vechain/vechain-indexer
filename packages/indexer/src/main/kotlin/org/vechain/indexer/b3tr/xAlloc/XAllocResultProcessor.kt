package org.vechain.indexer.b3tr.xAlloc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.xAlloc.repository.XAllocResultRepository
import org.vechain.indexer.version.IndexerVersionService

@Profile("b3tr", "b3tr-x-alloc")
@Component
open class XAllocResultProcessor(
    repository: XAllocResultRepository,
    xAllocResultArchiveService: ArchiveService<XAllocResult, XAllocResultArchive>,
    private val service: XAllocResultService,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = xAllocResultArchiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.X_ALLOC_RESULT,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Process the events using the service
        val (updated, existing) = service.processEvents(entry.events())

        // Save the updated NFTs and archives
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
