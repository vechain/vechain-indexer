package org.vechain.indexer.b3tr.action

import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.accumulateImpacts
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAction
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getEntity
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getValue
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByTo
import org.vechain.indexer.b3tr.action.IdUtils.generateId
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.EventUtils.groupByBlockNumber

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-all-time-action-summary")
open class UserAllTimeActionSummaryService(
    private val repository: UserAllTimeActionSummaryRepository,
    private val userAllTimeActionSummaryArchiveService:
        ArchiveService<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>,
) {

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<UserAllTimeActionSummary>, List<UserAllTimeActionSummary>> {

        val updatedResult = mutableMapOf<String, UserAllTimeActionSummary>()
        val archiveResult = mutableListOf<UserAllTimeActionSummary>()

        groupByBlockNumber(events).forEach { (_, blockEvents) ->
            // Process Users
            groupByTo(blockEvents).forEach { (userId, eventsPerUser) ->
                val recordId = generateId(userId)
                val existing = resolveExisting(recordId, updatedResult)
                val updated = createOrUpdateExisting(EntityType.USER, eventsPerUser, existing)
                existing?.let { archiveResult.add(it) }
                updatedResult[recordId] = updated
            }
            // Process Apps
            groupByAppId(blockEvents).forEach { (appId, eventsPerApp) ->
                val recordId = generateId(appId)
                val existing = resolveExisting(recordId, updatedResult)
                val updated = createOrUpdateExisting(EntityType.APP, eventsPerApp, existing)
                existing?.let { archiveResult.add(it) }
                updatedResult[recordId] = updated
            }

            // Process Global
            val recordId = generateId("GLOBAL")
            val existing = resolveExisting(recordId, updatedResult)
            val updated = createOrUpdateExisting(EntityType.GLOBAL, blockEvents, existing)
            existing?.let { archiveResult.add(it) }
            updatedResult[recordId] = updated
        }

        return updatedResult.values.toList() to archiveResult
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(
        updated: List<UserAllTimeActionSummary>,
        existing: List<UserAllTimeActionSummary>,
    ) {
        // Apply updates
        if (updated.isNotEmpty()) {
            repository.saveAll(updated)
        }

        // Apply archives
        if (existing.isNotEmpty()) {
            userAllTimeActionSummaryArchiveService.saveAll(existing)
        }
    }

    protected fun createOrUpdateExisting(
        entityType: EntityType,
        events: List<IndexedEvent>,
        existing: UserAllTimeActionSummary?,
    ): UserAllTimeActionSummary {
        require(events.isNotEmpty()) { "No events provided" }

        // All events must have the same 'to' and block number
        val blockNumber = events.first().blockNumber
        val entity = getEntity(events.first(), entityType)

        require(
            events.all { it.blockNumber == blockNumber && getEntity(it, entityType) == entity }
        ) {
            "All events must have the same block number and entity"
        }

        val rewardAmountIncrease = events.sumOf { getValue(it) }
        val proofs = events.mapNotNull { getAction(it).proof }
        val impacts = proofs.mapNotNull { it.impact }

        return if (existing != null) {
            require(existing.entity == entity) { "Entity mismatch" }

            UserAllTimeActionSummary(
                version = existing.version + 1,
                blockId = events.first().blockId,
                blockNumber = blockNumber,
                blockTimestamp = events.first().blockTimestamp,
                entity = existing.entity,
                entityType = existing.entityType,
                actionsRewarded = existing.actionsRewarded + events.size,
                totalRewardAmount = existing.totalRewardAmount + rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts + listOfNotNull(existing.totalImpact)),
            )
        } else {
            UserAllTimeActionSummary(
                version = 1,
                blockId = events.first().blockId,
                blockNumber = blockNumber,
                blockTimestamp = events.first().blockTimestamp,
                entity = entity,
                entityType = entityType,
                actionsRewarded = events.size.toLong(),
                totalRewardAmount = rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts),
            )
        }
    }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, UserAllTimeActionSummary>,
    ): UserAllTimeActionSummary? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
