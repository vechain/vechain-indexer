package org.vechain.indexer.b3tr.action

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
open class AppRoundActionSummaryProcessor(
    private val repository: AppRoundActionSummaryRepository,
    appRoundActionSummaryArchiveService:
        ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive>,
    private val service: AppRoundActionSummaryService,
    @param:Value("\${indexer.start-round.b3tr-sustainable-actions}") private val startRound: Int,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = appRoundActionSummaryArchiveService,
    ) {

    protected var roundId: Int =
        repository.findFirstByOrderByBlockNumberDesc()?.roundId ?: startRound

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        val (updated, archives, updatedRoundId) = service.processEvents(entry.events(), roundId)

        roundId = updatedRoundId

        // Save the updated NFTs and archives
        service.save(updated, archives)
    }
}
