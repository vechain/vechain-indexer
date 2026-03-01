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
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getReceiver
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByReceiver
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.validateAndFilterImpacts
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.b3tr.round.RoundUtils.discoverRoundId
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.IdUtils.generateId

@Service
@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
open class AppRoundActionSummaryService(
    private val repository: AppRoundActionSummaryRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val impactConfig: ActionImpactConfig,
) {

    open fun processEvents(
        events: List<IndexedEvent>,
        roundId: Int,
    ): Triple<List<AppRoundActionSummary>, List<AppRoundActionSummary>, Int> {
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
                groupByAppId(rewardDistributedEvents).forEach { (appId, appEvents) ->
                    groupByReceiver(appEvents).forEach { (receiverId, _) ->
                        allRecordIds.add(generateId(appId, receiverId, "$preloadRoundId"))
                    }
                }
            }
        }
        val preloaded =
            if (allRecordIds.isNotEmpty()) {
                repository.findAllById(allRecordIds).associateBy { it.getDocumentId() }
            } else {
                emptyMap()
            }

        val accumulator =
            VersionedDocumentAccumulator<AppRoundActionSummary>(
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

            groupByAppId(rewardDistributedEvents).forEach { (appId, appEvents) ->
                groupByReceiver(appEvents).forEach { (receiverId, receiverEvents) ->
                    val recordId = generateId(appId, receiverId, "$updatedRoundId")
                    val (existing, nextVersion) = accumulator.resolve(recordId)

                    val updated =
                        createOrUpdateExisting(
                            appId,
                            receiverId,
                            updatedRoundId,
                            receiverEvents,
                            blockDetails,
                            existing,
                            version = nextVersion,
                        )

                    accumulator.put(recordId, existing, updated)
                }
            }
        }

        val (updated, archived) = accumulator.results()
        return Triple(updated, archived, updatedRoundId)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<AppRoundActionSummary>, existing: List<AppRoundActionSummary>) {
        saveVersionedDocuments(
            updated,
            existing,
            mongoTemplate,
            inlineVersioningProperties.blockWindow,
            inlineVersioningProperties.maxVersions,
        )
    }

    protected fun createOrUpdateExisting(
        appId: String,
        receiverId: String,
        roundId: Int,
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        existing: AppRoundActionSummary?,
        version: Int,
    ): AppRoundActionSummary {
        require(events.isNotEmpty()) { "No events provided" }

        require(
            events.all {
                it.blockId == blockDetails.blockId &&
                    getReceiver(it) == receiverId &&
                    getAppId(it) == appId
            }
        ) {
            "All events must have the same block id, entity and appId"
        }

        val rewardAmountIncrease = events.sumOf { getAmount(it) }
        val proofs = events.mapNotNull { getAction(it).proof }
        val allImpacts = proofs.mapNotNull { it.impact }

        // Validate and filter impacts based on threshold
        val impacts = validateAndFilterImpacts(allImpacts, impactConfig)

        return if (existing != null) {
            require(existing.user == receiverId) { "User mismatch" }
            require(existing.appId == appId) { "App ID mismatch" }
            require(existing.roundId == roundId) { "Round mismatch" }

            AppRoundActionSummary(
                version = version,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                user = receiverId,
                appId = appId,
                roundId = roundId,
                actionsRewarded = existing.actionsRewarded + events.size,
                totalRewardAmount = existing.totalRewardAmount + rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts + listOfNotNull(existing.totalImpact)),
            )
        } else {
            AppRoundActionSummary(
                version = version,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                user = receiverId,
                appId = appId,
                roundId = roundId,
                actionsRewarded = events.size.toLong(),
                totalRewardAmount = rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts),
            )
        }
    }
}
