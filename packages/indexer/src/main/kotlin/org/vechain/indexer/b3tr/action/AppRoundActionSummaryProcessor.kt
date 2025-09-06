package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
open class AppRoundActionSummaryProcessor(
    repository: AppRoundActionSummaryRepository,
    appRoundActionSummaryArchiveService:
        ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive>,
    private val service: AppRoundActionSummaryService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = appRoundActionSummaryArchiveService,
    ) {
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
