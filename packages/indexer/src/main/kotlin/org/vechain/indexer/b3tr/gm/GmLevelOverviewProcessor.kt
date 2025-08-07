package org.vechain.indexer.b3tr.gm

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.repository.GmLevelOverviewRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("b3tr", "gm-nft-level-overview")
@Component
open class GmLevelOverviewProcessor(
    repository: GmLevelOverviewRepository,
    gmLevelOverviewArchiveService: ArchiveService<GmLevelOverview, GmLevelOverviewArchive>,
    private val service: GmLevelOverviewService,
) : BaseStatefulProcessor(repository = repository, archiveService = gmLevelOverviewArchiveService) {

    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }

        // Process the events using the service
        service.processEvents(matchedEvents)
    }
}
