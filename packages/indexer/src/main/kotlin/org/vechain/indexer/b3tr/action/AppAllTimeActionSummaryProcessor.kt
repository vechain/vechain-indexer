package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppAllTimeActionSummaryRepository
import org.vechain.indexer.version.IndexerVersionService

@Component
@Profile("b3tr", "b3tr-actions", "b3tr-app-all-time-action-summary")
open class AppAllTimeActionSummaryProcessor(
    repository: AppAllTimeActionSummaryRepository,
    appAllTimeActionSummaryArchiveService:
        ArchiveService<AppAllTimeActionSummary, AppAllTimeActionSummaryArchive>,
    private val service: AppAllTimeActionSummaryService,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = appAllTimeActionSummaryArchiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.APP_ALL_TIME_ACTION_SUMMARY,
    ) {
    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Process the events using the service
        val (updated, archives) = service.processEvents(entry.events())

        // Save the updated NFTs and archives
        service.save(updated, archives)
    }
}
