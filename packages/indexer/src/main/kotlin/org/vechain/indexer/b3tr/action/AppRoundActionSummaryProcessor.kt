package org.vechain.indexer.b3tr.action

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.version.IndexerVersionService

@Component
@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
open class AppRoundActionSummaryProcessor(
    private val repository: AppRoundActionSummaryRepository,
    private val service: AppRoundActionSummaryService,
    @param:Value("\${indexer.start-round.b3tr-sustainable-actions}") private val startRound: Int,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository,
        indexerVersionService,
        IndexerNames.APP_ROUND_ACTION_SUMMARY,
    ) {

    protected var roundId: Int =
        repository.findFirstByOrderByBlockNumberDesc()?.roundId ?: startRound

    override fun rollback(blockNumber: Long) {
        super.rollback(blockNumber)
        // Reset roundId from the latest record after rollback
        roundId = repository.findFirstByOrderByBlockNumberDesc()?.roundId ?: startRound
    }

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        val (updated, existing, updatedRoundId) = service.processEvents(entry.events(), roundId)

        roundId = updatedRoundId

        // Save the updated records
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
