package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppDailyActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timing.WithTiming

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-daily-action-summary")
open class AppDailyActionSummaryProcessor(
    repository: AppDailyActionSummaryRepository,
    appDailyActionSummaryArchiveService:
        ArchiveService<AppDailyActionSummary, AppDailyActionSummaryArchive>,
    private val service: AppDailyActionSummaryService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = appDailyActionSummaryArchiveService,
    ) {
    @WithTiming("AppDailyActionSummaryProcessor.process")
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
