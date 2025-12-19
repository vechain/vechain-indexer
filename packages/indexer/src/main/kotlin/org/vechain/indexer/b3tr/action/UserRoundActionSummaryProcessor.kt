package org.vechain.indexer.b3tr.action

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.version.IndexerVersionService

@Component
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryProcessor(
    private val repository: UserRoundActionSummaryRepository,
    userRoundActionSummaryArchiveService:
        ArchiveService<UserRoundActionSummary, UserRoundActionSummaryArchive>,
    private val service: UserRoundActionSummaryService,
    @param:Value("\${indexer.start-round.b3tr-sustainable-actions}") private val startRound: Int,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = userRoundActionSummaryArchiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.USER_ROUND_ACTION_SUMMARY,
    ) {

    protected var roundId: Int =
        repository.findFirstByOrderByBlockNumberDesc()?.roundId ?: startRound

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        val (updated, archives, updatedRoundId) = service.processEvents(entry.events(), roundId)

        roundId = updatedRoundId

        // Save the updated NFTs and archives
        withContext(Dispatchers.IO) { service.save(updated, archives) }
    }
}
