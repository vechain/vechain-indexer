package org.vechain.indexer.b3tr.action

import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.repository.UserDailyActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.EventUtils.groupByBlockNumber

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-daily-action-summary")
open class UserDailyActionSummaryService(
    private val repository: UserDailyActionSummaryRepository,
    private val userDailyActionSummaryArchiveService:
        ArchiveService<UserDailyActionSummary, UserDailyActionSummaryArchive>,
) {

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<UserDailyActionSummary>, List<UserDailyActionSummary>> {

        val updatedResult = mutableMapOf<String, UserDailyActionSummary>()
        val archiveResult = mutableListOf<UserDailyActionSummary>()

        groupByBlockNumber(events).forEach { (_, blockEvents) ->
            //            groupByDate(blockEvents).forEach { (date, eventsPerDate) ->
        }

        return updatedResult.values.toList() to archiveResult
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<UserDailyActionSummary>, existing: List<UserDailyActionSummary>) {
        // Apply updates
        if (updated.isNotEmpty()) {
            repository.saveAll(updated)
        }

        // Apply archives
        if (existing.isNotEmpty()) {
            userDailyActionSummaryArchiveService.saveAll(existing)
        }
    }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, UserDailyActionSummary>,
    ): UserDailyActionSummary? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
