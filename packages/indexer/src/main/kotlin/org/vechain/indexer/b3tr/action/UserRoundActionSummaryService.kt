package org.vechain.indexer.b3tr.action

import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.accumulateImpacts
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAction
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAmount
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getEntity
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByReceiver
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.validateAndFilterImpacts
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.round.RoundUtils.discoverRoundId
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.IdUtils.generateId

@Service
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryService(
    private val repository: UserRoundActionSummaryRepository,
    private val appRoundRepo: AppRoundActionSummaryRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
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

        // Pre-collect all record IDs by simulating round discovery
        val allRecordIds = mutableSetOf<String>()
        var preloadRoundId = roundId
        groupByBlock(events).forEach { (_, blockEvents) ->
            val roundChangeEvents =
                blockEvents.filter {
                    it.eventType == "EmissionDistributed" || it.eventType == "EmissionDistributedV2"
                }
            val rewardDistributedEvents = blockEvents.filter { it.eventType == "B3TR_ActionReward" }
            preloadRoundId = discoverRoundId(roundChangeEvents, preloadRoundId)
            if (rewardDistributedEvents.isNotEmpty()) {
                groupByReceiver(rewardDistributedEvents).forEach { (userId, _) ->
                    allRecordIds.add(generateId(userId, "$preloadRoundId"))
                }
                groupByAppId(rewardDistributedEvents).forEach { (appId, _) ->
                    allRecordIds.add(generateId(appId, "$preloadRoundId"))
                }
                allRecordIds.add(generateId(EntityType.GLOBAL.name, "$preloadRoundId"))
            }
        }
        val preloaded =
            if (allRecordIds.isNotEmpty()) {
                repository.findAllById(allRecordIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<UserRoundActionSummary>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) }
            )
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

            var blockNewUsers = 0L

            // Process Users
            groupByReceiver(rewardDistributedEvents).forEach { (userId, eventsPerReceiver) ->
                val recordId = generateId(userId, "$updatedRoundId")
                val (existing, nextVersion) = accumulator.resolve(recordId)
                if (existing == null) {
                    blockNewUsers++
                }
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

            // Process Global with incremental unique user count
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
                    .copy(
                        totalUniqueUserInteractions =
                            (existing?.totalUniqueUserInteractions ?: 0) + blockNewUsers
                    )
            accumulator.put(recordId, existing, updated)
        }

        val (results, archived) = accumulator.results()

        // Set per-app unique user counts from the app-level collection (single batch query)
        val appEntities = results.filter { it.entityType == EntityType.APP }
        val appPairs = appEntities.map { it.entity to it.roundId }.toSet()
        val appCounts = appRoundRepo.countByAppIdAndRoundIdPairs(appPairs)

        val adjustedResults =
            results.map { doc ->
                if (doc.entityType == EntityType.APP) {
                    doc.copy(
                        totalUniqueUserInteractions = appCounts[doc.entity to doc.roundId] ?: 0
                    )
                } else {
                    doc
                }
            }

        return Triple(adjustedResults, archived, updatedRoundId)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<UserRoundActionSummary>, existing: List<UserRoundActionSummary>) {
        saveVersionedDocuments(
            updated,
            existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
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
