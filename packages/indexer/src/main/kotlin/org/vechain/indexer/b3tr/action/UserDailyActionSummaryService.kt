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
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAction
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAmount
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getEntity
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByReceiver
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.validateAndFilterImpacts
import org.vechain.indexer.b3tr.action.repository.AppDailyActionSummaryRepository
import org.vechain.indexer.b3tr.action.repository.UserDailyActionSummaryRepository
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.BlockUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.IdUtils.generateId

@Service
@Profile("b3tr", "b3tr-actions", "b3tr-user-daily-action-summary")
open class UserDailyActionSummaryService(
    private val repository: UserDailyActionSummaryRepository,
    private val appDailyRepo: AppDailyActionSummaryRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val impactConfig: ActionImpactConfig,
) {

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<UserDailyActionSummary>, List<UserDailyActionSummary>> {

        // Pre-collect all record IDs and batch-load from DB
        val allRecordIds = mutableSetOf<String>()
        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            val date = BlockUtils.getDateAtUTC(blockDetails.blockTimestamp)
            groupByReceiver(blockEvents).forEach { (userId, _) ->
                allRecordIds.add(generateId(userId, date))
            }
            groupByAppId(blockEvents).forEach { (appId, _) ->
                allRecordIds.add(generateId(appId, date))
            }
            allRecordIds.add(generateId(EntityType.GLOBAL.name, date))
        }
        val preloaded =
            if (allRecordIds.isNotEmpty()) {
                repository.findAllById(allRecordIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<UserDailyActionSummary>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) }
            )

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            // Get date from block timestamp (preserving UTC)
            val date = BlockUtils.getDateAtUTC(blockDetails.blockTimestamp)
            var blockNewUsers = 0L

            // Process Users
            groupByReceiver(blockEvents).forEach { (userId, eventsPerReceiver) ->
                val recordId = generateId(userId, date)
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
                        existing,
                        version = nextVersion,
                    )
                accumulator.put(recordId, existing, updated)
            }

            // Process Apps
            groupByAppId(blockEvents).forEach { (appId, eventsPerApp) ->
                val recordId = generateId(appId, date)
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

            // Process Global with incremental unique user count
            val recordId = generateId(EntityType.GLOBAL.name, date)
            val (existing, nextVersion) = accumulator.resolve(recordId)
            val updated =
                createOrUpdateExisting(
                        EntityType.GLOBAL.name,
                        EntityType.GLOBAL,
                        blockEvents,
                        blockDetails,
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

        // Set per-app unique user counts from the app-level collection
        val appEntities = results.filter { it.entityType == EntityType.APP }
        val appCounts =
            appEntities.associate {
                (it.entity to it.date) to appDailyRepo.countByAppIdAndDate(it.entity, it.date)
            }

        val adjustedResults =
            results.map { doc ->
                if (doc.entityType == EntityType.APP) {
                    doc.copy(totalUniqueUserInteractions = appCounts[doc.entity to doc.date] ?: 0)
                } else {
                    doc
                }
            }

        return adjustedResults to archived
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<UserDailyActionSummary>, existing: List<UserDailyActionSummary>) {
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
        existing: UserDailyActionSummary?,
        version: Int,
    ): UserDailyActionSummary {
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
            UserDailyActionSummary(
                version = version,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                entityType = entityType,
                entity = entity,
                date = existing.date,
                actionsRewarded = existing.actionsRewarded + events.size,
                totalRewardAmount = existing.totalRewardAmount + rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts + listOfNotNull(existing.totalImpact)),
            )
        } else {
            UserDailyActionSummary(
                version = version,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                entityType = entityType,
                entity = entity,
                date = BlockUtils.getDateAtUTC(blockDetails.blockTimestamp),
                actionsRewarded = events.size.toLong(),
                totalRewardAmount = rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts),
            )
        }
    }
}
