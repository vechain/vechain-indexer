package org.vechain.indexer.b3tr.action

import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.accumulateImpacts
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAction
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAmount
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getEntity
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByReceiver
import org.vechain.indexer.b3tr.action.IdUtils.generateId
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryService(
    private val repository: UserRoundActionSummaryRepository,
    private val userRoundActionSummaryArchiveService:
        ArchiveService<UserRoundActionSummary, UserRoundActionSummaryArchive>,
    private val userRoundActionSummaryPruner: Pruner,
) {

    open fun processEvents(
        blockDetails: BlockDetails,
        events: List<IndexedEvent>,
        roundId: Int,
    ): Pair<List<UserRoundActionSummary>, List<UserRoundActionSummary>> {
        assertEventTypes(events, "B3TR_ActionReward")

        val updatedResult = mutableMapOf<String, UserRoundActionSummary>()
        val archiveResult = mutableListOf<UserRoundActionSummary>()

        // Process Users
        groupByReceiver(events).forEach { (userId, eventsPerReceiver) ->
            val recordId = generateId(userId, "$roundId")
            val existing = resolveExisting(recordId, updatedResult)
            val updated =
                createOrUpdateExisting(
                    userId,
                    EntityType.USER,
                    eventsPerReceiver,
                    blockDetails,
                    roundId,
                    existing,
                )
            existing?.let { archiveResult.add(it) }
            updatedResult[recordId] = updated
        }

        // Process Apps
        groupByAppId(events).forEach { (appId, eventsPerApp) ->
            val recordId = generateId(appId, "$roundId")
            val existing = resolveExisting(recordId, updatedResult)
            val updated =
                createOrUpdateExisting(
                    appId,
                    EntityType.APP,
                    eventsPerApp,
                    blockDetails,
                    roundId,
                    existing,
                )
            existing?.let { archiveResult.add(it) }
            updatedResult[recordId] = updated
        }

        // Process Global
        val recordId = generateId(EntityType.GLOBAL.name, "$roundId")
        val existing = resolveExisting(recordId, updatedResult)
        val updated =
            createOrUpdateExisting(
                EntityType.GLOBAL.name,
                EntityType.GLOBAL,
                events,
                blockDetails,
                roundId,
                existing,
            )
        existing?.let { archiveResult.add(it) }
        updatedResult[recordId] = updated

        return updatedResult.values.toList() to archiveResult
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<UserRoundActionSummary>, existing: List<UserRoundActionSummary>) {
        saveVersionedDocuments(
            updated,
            existing,
            repository,
            userRoundActionSummaryArchiveService,
            userRoundActionSummaryPruner,
        )
    }

    protected fun createOrUpdateExisting(
        entity: String,
        entityType: EntityType,
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        roundId: Int,
        existing: UserRoundActionSummary?,
    ): UserRoundActionSummary {
        require(
            events.all { it.blockId == blockDetails.blockId && getEntity(it, entityType) == entity }
        ) {
            "All events must have the same block id and entity"
        }

        val rewardAmountIncrease = events.sumOf { getAmount(it) }
        val proofs = events.mapNotNull { getAction(it).proof }
        val impacts = proofs.mapNotNull { it.impact }

        return if (existing != null) {
            require(existing.entity == entity) { "Entity mismatch" }
            UserRoundActionSummary(
                version = existing.version + 1,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                entityType = entityType,
                entity = entity,
                roundId = roundId,
                actionsRewarded = existing.actionsRewarded + events.size,
                totalRewardAmount = existing.totalRewardAmount + rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts + listOfNotNull(existing.totalImpact)),
            )
        } else {
            UserRoundActionSummary(
                version = 1,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                entityType = entityType,
                entity = entity,
                roundId = roundId,
                actionsRewarded = events.size.toLong(),
                totalRewardAmount = rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts),
            )
        }
    }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, UserRoundActionSummary>,
    ): UserRoundActionSummary? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
