package org.vechain.indexer.b3tr.action

import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.accumulateImpacts
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAction
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAmount
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getEntity
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByReceiver
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.validateAndFilterImpacts
import org.vechain.indexer.b3tr.action.repository.UserAllTimeActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.IdUtils.generateId

@Service
@Profile("b3tr", "b3tr-actions", "b3tr-user-all-time-action-summary")
open class UserAllTimeActionSummaryService(
    private val repository: UserAllTimeActionSummaryRepository,
    private val userAllTimeActionSummaryArchiveService:
        ArchiveService<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>,
    private val userAllTimeActionSummaryPruner:
        TargetedPruner<UserAllTimeActionSummary, UserAllTimeActionSummaryArchive>,
    private val impactConfig: ActionImpactConfig,
) {
    private val globalId = generateId(EntityType.GLOBAL.name)

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<UserAllTimeActionSummary>, List<UserAllTimeActionSummary>> {
        assertEventTypes(events, "B3TR_ActionReward")

        val accumulator =
            VersionedDocumentAccumulator<UserAllTimeActionSummary>(repository::findByIdOrNull)

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            // Process Users
            groupByReceiver(blockEvents).forEach { (userId, eventsPerReceiver) ->
                val recordId = generateId(userId)
                val (existing, nextVersion) = accumulator.resolve(recordId)
                val updated =
                    createOrUpdateExisting(
                        userId,
                        EntityType.USER,
                        eventsPerReceiver,
                        blockDetails,
                        existing,
                        version = nextVersion,
                    )
                accumulator.put(recordId, existing, updated)
            }
            // Process Apps
            groupByAppId(blockEvents).forEach { (appId, eventsPerApp) ->
                val recordId = generateId(appId)
                val (existing, nextVersion) = accumulator.resolve(recordId)
                val updated =
                    createOrUpdateExisting(
                        appId,
                        EntityType.APP,
                        eventsPerApp,
                        blockDetails,
                        existing,
                        version = nextVersion,
                    )
                accumulator.put(recordId, existing, updated)
            }

            // Process Global
            val (existing, nextVersion) = accumulator.resolve(globalId)
            val updated =
                createOrUpdateExisting(
                    EntityType.GLOBAL.name,
                    EntityType.GLOBAL,
                    blockEvents,
                    blockDetails,
                    existing,
                    version = nextVersion,
                )
            accumulator.put(globalId, existing, updated)
        }

        return accumulator.results()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(
        updated: List<UserAllTimeActionSummary>,
        existing: List<UserAllTimeActionSummary>,
    ) {
        saveVersionedDocuments(
            updated,
            existing,
            userAllTimeActionSummaryArchiveService,
            userAllTimeActionSummaryPruner,
        )
    }

    protected fun createOrUpdateExisting(
        entity: String,
        entityType: EntityType,
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        existing: UserAllTimeActionSummary?,
        version: Int,
    ): UserAllTimeActionSummary {
        require(events.isNotEmpty()) { "No events provided" }

        require(
            events.all { it.blockId == blockDetails.blockId && getEntity(it, entityType) == entity }
        ) {
            "All events must have the same block id and entity"
        }

        val rewardAmountIncrease = events.sumOf { getAmount(it) }
        val proofs = events.mapNotNull { getAction(it).proof }
        val allImpacts = proofs.mapNotNull { it.impact }

        // Validate and filter impacts based on threshold
        val impacts = validateAndFilterImpacts(allImpacts, impactConfig)

        return if (existing != null) {
            require(existing.entity == entity) { "Entity mismatch" }

            UserAllTimeActionSummary(
                version = version,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                entity = existing.entity,
                entityType = existing.entityType,
                actionsRewarded = existing.actionsRewarded + events.size,
                totalRewardAmount = existing.totalRewardAmount + rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts + listOfNotNull(existing.totalImpact)),
            )
        } else {
            UserAllTimeActionSummary(
                version = version,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                entity = entity,
                entityType = entityType,
                actionsRewarded = events.size.toLong(),
                totalRewardAmount = rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts),
            )
        }
    }
}
