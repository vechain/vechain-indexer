package org.vechain.indexer.b3tr.action

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timing.WithTiming

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryProcessor(
    private val repository: UserRoundActionSummaryRepository,
    userRoundActionSummaryArchiveService:
        ArchiveService<UserRoundActionSummary, UserRoundActionSummaryArchive>,
    private val service: UserRoundActionSummaryService,
    @param:Value("\${indexer.start-round.b3tr-sustainable-actions}") private val startRound: Int,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = userRoundActionSummaryArchiveService,
    ) {

    protected var roundId: Int =
        repository.findFirstByOrderByBlockNumberDesc()?.roundId ?: startRound

    @WithTiming("UserRoundActionSummaryProcessor.process")
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }

        val (updated, archives, updatedRoundId) = service.processEvents(matchedEvents, roundId)

        roundId = updatedRoundId

        // Save the updated NFTs and archives
        service.save(updated, archives)
    }
}
