package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.AppAllTimeActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-all-time-action-summary")
open class AppAllTimeActionSummaryService(
    private val repository: AppAllTimeActionSummaryRepository,
    private val appAllTimeActionSummaryArchiveService:
        ArchiveService<AppAllTimeActionSummary, AppAllTimeActionSummaryArchive>,
) {

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<AppAllTimeActionSummary>, List<AppAllTimeActionSummary>> {

        val updatedResult = mutableMapOf<String, AppAllTimeActionSummary>()
        val archiveResult = mutableListOf<AppAllTimeActionSummary>()

        // TODO implement logic to process events and update summaries

        return updatedResult.values.toList() to archiveResult
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<AppAllTimeActionSummary>, existing: List<AppAllTimeActionSummary>) {
        // Apply updates
        if (updated.isNotEmpty()) {
            repository.saveAll(updated)
        }

        // Apply archives
        if (existing.isNotEmpty()) {
            appAllTimeActionSummaryArchiveService.saveAll(existing)
        }
    }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, AppAllTimeActionSummary>,
    ): AppAllTimeActionSummary? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
