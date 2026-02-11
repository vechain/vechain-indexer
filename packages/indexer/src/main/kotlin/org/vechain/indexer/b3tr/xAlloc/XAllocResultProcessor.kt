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
import org.vechain.indexer.checkpoint.CheckpointService

@Profile("b3tr", "b3tr-x-alloc")
@Component
open class XAllocResultProcessor(
    repository: XAllocResultRepository,
    xAllocResultArchiveService: ArchiveService<XAllocResult, XAllocResultArchive>,
    private val service: XAllocResultService,
    checkpointService: CheckpointService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = xAllocResultArchiveService,
        indexerName = IndexerNames.X_ALLOC_RESULT,
        checkpointService = checkpointService,
        collectionName = "b3tr_x_alloc_results",
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
