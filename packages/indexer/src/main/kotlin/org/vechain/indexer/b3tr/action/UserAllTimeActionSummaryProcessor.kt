package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timing.WithTiming

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-all-time-action-summary")
open class UserAllTimeActionSummaryProcessor(
    repository: UserAllTimeActionSummaryRepository,
    userAllTimeActionSummaryArchiveService:
        ArchiveService<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>,
    private val service: UserAllTimeActionSummaryService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = userAllTimeActionSummaryArchiveService,
    ) {
    @WithTiming("UserAllTimeActionSummaryProcessor.process")
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
