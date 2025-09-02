package org.vechain.indexer.b3tr.xAlloc

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.voting.repository.XAllocResultRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("b3tr", "b3tr-x-alloc")
@Component
open class XAllocResultProcessor(
    repository: XAllocResultRepository,
    xAllocResultArchiveService: ArchiveService<XAllocResult, XAllocResultArchive>,
    private val service: XAllocResultService,
) : BaseStatefulProcessor(repository = repository, archiveService = xAllocResultArchiveService) {
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }

        // Process the events using the service
        val (updated, archives) = service.processEvents(matchedEvents)

        // Save the updated NFTs and archives
        service.save(updated, archives)
    }
}
