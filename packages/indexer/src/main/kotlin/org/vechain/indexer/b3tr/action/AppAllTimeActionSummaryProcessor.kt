package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppAllTimeActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timing.WithTiming

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-all-time-action-summary")
open class AppAllTimeActionSummaryProcessor(
    repository: AppAllTimeActionSummaryRepository,
    appAllTimeActionSummaryArchiveService:
        ArchiveService<AppAllTimeActionSummary, AppAllTimeActionSummaryArchive>,
    private val service: AppAllTimeActionSummaryService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = appAllTimeActionSummaryArchiveService,
    ) {
    @WithTiming("AppAllTimeActionSummaryProcessor.process")
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
