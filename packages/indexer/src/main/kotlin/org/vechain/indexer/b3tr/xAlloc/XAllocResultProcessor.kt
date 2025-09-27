package org.vechain.indexer.b3tr.xAlloc

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.xAlloc.repository.XAllocResultRepository
import org.vechain.indexer.timing.WithTiming

@Profile("b3tr", "b3tr-x-alloc")
@Component
open class XAllocResultProcessor(
    repository: XAllocResultRepository,
    xAllocResultArchiveService: ArchiveService<XAllocResult, XAllocResultArchive>,
    private val service: XAllocResultService,
) : BaseStatefulProcessor(repository = repository, archiveService = xAllocResultArchiveService) {
    @WithTiming("XAllocResultProcessor.process")
    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Process the events using the service
        val (updated, archives) = service.processEvents(entry.events())

        // Save the updated NFTs and archives
        service.save(updated, archives)
    }
}
