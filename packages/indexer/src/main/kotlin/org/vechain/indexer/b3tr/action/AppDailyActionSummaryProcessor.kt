package org.vechain.indexer.b3tr.action

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppDailyActionSummaryRepository
import org.vechain.indexer.checkpoint.CheckpointService

@Component
@Profile("b3tr", "b3tr-actions", "b3tr-app-daily-action-summary")
open class AppDailyActionSummaryProcessor(
    repository: AppDailyActionSummaryRepository,
    appDailyActionSummaryArchiveService: ArchiveService<AppDailyActionSummary>,
    private val service: AppDailyActionSummaryService,
    checkpointService: CheckpointService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = appDailyActionSummaryArchiveService,
        indexerName = IndexerNames.APP_DAILY_ACTION_SUMMARY.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.APP_DAILY_ACTION_SUMMARY.COLLECTION,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Process the events using the service
        val (updated, existing) = service.processEvents(entry.events())

        // Save the updated NFTs and archives
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
