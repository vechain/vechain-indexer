package org.vechain.indexer.b3tr.gm

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("b3tr", "gm-nft")
@Component
open class GmNftProcessor(
    repository: GmNftRepository,
    gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>,
    private val service: GmNftService,
) : BaseStatefulProcessor(repository = repository, archiveService = gmNftArchiveService) {
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }

        // Process the events using the service
        service.processEvents(matchedEvents)
    }
}
