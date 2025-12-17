package org.vechain.indexer.b3tr.action

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.UserDailyActionSummaryRepository
import org.vechain.indexer.version.IndexerVersionService

@Component
@Profile("b3tr", "b3tr-actions", "b3tr-user-daily-action-summary")
open class UserDailyActionSummaryProcessor(
    repository: UserDailyActionSummaryRepository,
    userDailyActionSummaryArchiveService:
        ArchiveService<UserDailyActionSummary, UserDailyActionSummaryArchive>,
    private val service: UserDailyActionSummaryService,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = userDailyActionSummaryArchiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.USER_DAILY_ACTION_SUMMARY,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Process the events using the service
        val (updated, archives) = service.processEvents(entry.events())

        // Save the updated NFTs and archives
        withContext(Dispatchers.IO) { service.save(updated, archives) }
    }
}
