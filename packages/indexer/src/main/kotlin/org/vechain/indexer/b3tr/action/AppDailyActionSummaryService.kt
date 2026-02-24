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
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getReceiver
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByReceiver
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.validateAndFilterImpacts
import org.vechain.indexer.b3tr.action.repository.AppDailyActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.BlockUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.IdUtils.generateId

@Service
@Profile("b3tr", "b3tr-actions", "b3tr-app-daily-action-summary")
open class AppDailyActionSummaryService(
    private val repository: AppDailyActionSummaryRepository,
    private val appDailyActionSummaryArchiveService:
        ArchiveService<AppDailyActionSummary, AppDailyActionSummaryArchive>,
    private val appDailyActionSummaryPruner:
        TargetedPruner<AppDailyActionSummary, AppDailyActionSummaryArchive>,
    private val impactConfig: ActionImpactConfig,
) {

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<AppDailyActionSummary>, List<AppDailyActionSummary>> {
        assertEventTypes(events, "B3TR_ActionReward")

        val accumulator =
            VersionedDocumentAccumulator<AppDailyActionSummary>(repository::findByIdOrNull)

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            // Get date from block timestamp (preserving UTC)
            val date = BlockUtils.getDateAtUTC(blockDetails.blockTimestamp)

            groupByAppId(blockEvents).forEach { (appId, appEvents) ->
                groupByReceiver(appEvents).forEach { (receiverId, receiverEvents) ->
                    val recordId = generateId(appId, receiverId, date)
                    val (existing, nextVersion) = accumulator.resolve(recordId)

                    val updated =
                        createOrUpdateExisting(
                            appId,
                            receiverId,
                            date,
                            receiverEvents,
                            blockDetails,
                            existing,
                            version = nextVersion,
                        )

                    accumulator.put(recordId, existing, updated)
                }
            }
        }

        return accumulator.results()
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<AppDailyActionSummary>, existing: List<AppDailyActionSummary>) {
        saveVersionedDocuments(
            updated,
            existing,
            appDailyActionSummaryArchiveService,
            appDailyActionSummaryPruner,
        )
    }

    protected fun createOrUpdateExisting(
        appId: String,
        receiverId: String,
        date: String,
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        existing: AppDailyActionSummary?,
        version: Int,
    ): AppDailyActionSummary {
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
            require(existing.date == date) { "Date mismatch" }

            AppDailyActionSummary(
                version = version,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                user = receiverId,
                appId = appId,
                date = date,
                actionsRewarded = existing.actionsRewarded + events.size,
                totalRewardAmount = existing.totalRewardAmount + rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts + listOfNotNull(existing.totalImpact)),
            )
        } else {
            AppDailyActionSummary(
                version = version,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                user = receiverId,
                appId = appId,
                date = date,
                actionsRewarded = events.size.toLong(),
                totalRewardAmount = rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts),
            )
        }
    }
}
