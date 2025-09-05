package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryService(
    private val repository: UserRoundActionSummaryRepository,
    private val userRoundActionSummaryArchiveService:
        ArchiveService<UserRoundActionSummary, UserRoundActionSummaryArchive>,
) {

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<UserRoundActionSummary>, List<UserRoundActionSummary>> {

        val updatedResult = mutableMapOf<String, UserRoundActionSummary>()
        val archiveResult = mutableListOf<UserRoundActionSummary>()

        // TODO implement logic to process events and update summaries

        return updatedResult.values.toList() to archiveResult
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<UserRoundActionSummary>, existing: List<UserRoundActionSummary>) {
        // Apply updates
        if (updated.isNotEmpty()) {
            repository.saveAll(updated)
        }

        // Apply archives
        if (existing.isNotEmpty()) {
            userRoundActionSummaryArchiveService.saveAll(existing)
        }
    }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, UserRoundActionSummary>,
    ): UserRoundActionSummary? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
