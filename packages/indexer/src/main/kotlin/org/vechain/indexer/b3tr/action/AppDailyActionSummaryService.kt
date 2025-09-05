package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-daily-action-summary")
open class AppDailyActionSummaryService(
    private val repository: AppRoundActionSummaryRepository,
    private val appRoundActionSummaryArchiveService:
        ArchiveService<AppRoundActionSummary, AppRoundActionSummaryArchive>,
) {

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<AppRoundActionSummary>, List<AppRoundActionSummary>> {

        val updatedResult = mutableMapOf<String, AppRoundActionSummary>()
        val archiveResult = mutableListOf<AppRoundActionSummary>()

        // TODO implement logic to process events and update summaries

        return updatedResult.values.toList() to archiveResult
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<AppRoundActionSummary>, existing: List<AppRoundActionSummary>) {
        // Apply updates
        if (updated.isNotEmpty()) {
            repository.saveAll(updated)
        }

        // Apply archives
        if (existing.isNotEmpty()) {
            appRoundActionSummaryArchiveService.saveAll(existing)
        }
    }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, AppRoundActionSummary>,
    ): AppRoundActionSummary? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
