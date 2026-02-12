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
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.round.RoundUtils.discoverRoundId
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.IdUtils.generateId

@Service
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryService(
    private val repository: UserRoundActionSummaryRepository,
    private val userRoundActionSummaryArchiveService:
        ArchiveService<UserRoundActionSummary, UserRoundActionSummaryArchive>,
    private val userRoundActionSummaryPruner:
        TargetedPruner<UserRoundActionSummary, UserRoundActionSummaryArchive>,
    private val impactConfig: ActionImpactConfig,
) {

    open fun processEvents(
        events: List<IndexedEvent>,
        roundId: Int,
    ): Triple<List<UserRoundActionSummary>, List<UserRoundActionSummary>, Int> {
        assertEventTypes(
            events,
            "B3TR_ActionReward",
            "EmissionDistributed",
            "EmissionDistributedV2",
        )

        val accumulator =
            VersionedDocumentAccumulator<UserRoundActionSummary>(repository::findByIdOrNull)
        var updatedRoundId = roundId

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            val roundChangeEvents =
                blockEvents.filter {
                    (it.eventType == "EmissionDistributed" ||
                        it.eventType == "EmissionDistributedV2")
                }
            val rewardDistributedEvents = blockEvents.filter { it.eventType == "B3TR_ActionReward" }

            // Ensure no unexpected events are present
            require(roundChangeEvents.size + rewardDistributedEvents.size == blockEvents.size) {
                "Unexpected event types found in block ${blockDetails.blockNumber}"
            }
            updatedRoundId = discoverRoundId(roundChangeEvents, updatedRoundId)

            if (rewardDistributedEvents.isEmpty()) {
                // No relevant events to process in this block
                return@forEach
            }

            // Process Users
            groupByReceiver(rewardDistributedEvents).forEach { (userId, eventsPerReceiver) ->
                val recordId = generateId(userId, "$updatedRoundId")
                val (existing, nextVersion) = accumulator.resolve(recordId)
                val updated =
                    createOrUpdateExisting(
                        userId,
                        EntityType.USER,
                        eventsPerReceiver,
                        blockDetails,
                        updatedRoundId,
                        existing,
                        version = nextVersion,
                    )
                accumulator.put(recordId, existing, updated)
            }

            // Process Apps
            groupByAppId(rewardDistributedEvents).forEach { (appId, eventsPerApp) ->
                val recordId = generateId(appId, "$updatedRoundId")
                val (existing, nextVersion) = accumulator.resolve(recordId)
                val updated =
                    createOrUpdateExisting(
                        appId,
                        EntityType.APP,
                        eventsPerApp,
                        blockDetails,
                        updatedRoundId,
                        existing,
                        version = nextVersion,
                    )
                accumulator.put(recordId, existing, updated)
            }

            // Process Global
            val recordId = generateId(EntityType.GLOBAL.name, "$updatedRoundId")
            val (existing, nextVersion) = accumulator.resolve(recordId)
            val updated =
                createOrUpdateExisting(
                    EntityType.GLOBAL.name,
                    EntityType.GLOBAL,
                    rewardDistributedEvents,
                    blockDetails,
                    updatedRoundId,
                    existing,
                    version = nextVersion,
                )
            accumulator.put(recordId, existing, updated)
        }

        val (updated, archived) = accumulator.results()
        return Triple(updated, archived, updatedRoundId)
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
        version: Int,
    ): UserRoundActionSummary {
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
            UserRoundActionSummary(
                version = version,
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
                version = version,
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
}
