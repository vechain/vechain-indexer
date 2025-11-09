package org.vechain.indexer.b3tr.action

import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.accumulateImpacts
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAction
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAmount
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getReceiver
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByAppId
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.groupByReceiver
import org.vechain.indexer.b3tr.action.repository.AppAllTimeActionSummaryRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.IdUtils.generateId

@Service
@Profile("b3tr", "b3tr-actions", "b3tr-app-all-time-action-summary")
open class AppAllTimeActionSummaryService(
    private val repository: AppAllTimeActionSummaryRepository,
    private val appAllTimeActionSummaryArchiveService:
        ArchiveService<AppAllTimeActionSummary, AppAllTimeActionSummaryArchive>,
    private val appAllTimeActionSummaryPruner:
        TargetedPruner<AppAllTimeActionSummary, AppAllTimeActionSummaryArchive>,
) {

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<AppAllTimeActionSummary>, List<AppAllTimeActionSummary>> {
        assertEventTypes(events, "B3TR_ActionReward")

        val updatedResult = mutableMapOf<String, AppAllTimeActionSummary>()
        val archiveResult = mutableListOf<AppAllTimeActionSummary>()

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            groupByAppId(blockEvents).forEach { (appId, appEvents) ->
                groupByReceiver(appEvents).forEach { (receiverId, receiverEvents) ->
                    val recordId = generateId(appId, receiverId)
                    val existing = resolveExisting(recordId, updatedResult)
                    val updated =
                        createOrUpdateExisting(
                            appId,
                            receiverId,
                            receiverEvents,
                            blockDetails,
                            existing,
                        )
                    existing?.let { archiveResult.add(it) }
                    updatedResult[recordId] = updated
                }
            }
        }

        return updatedResult.values.toList() to archiveResult
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<AppAllTimeActionSummary>, existing: List<AppAllTimeActionSummary>) {
        saveVersionedDocuments(
            updated,
            existing,
            repository,
            appAllTimeActionSummaryArchiveService,
            appAllTimeActionSummaryPruner,
        )
    }

    protected fun createOrUpdateExisting(
        appId: String,
        receiverId: String,
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        existing: AppAllTimeActionSummary?,
    ): AppAllTimeActionSummary {
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
        val impacts = proofs.mapNotNull { it.impact }

        return if (existing != null) {
            require(existing.user == receiverId) { "User mismatch" }
            require(existing.appId == appId) { "App ID mismatch" }

            AppAllTimeActionSummary(
                version = existing.version + 1,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                user = receiverId,
                appId = appId,
                actionsRewarded = existing.actionsRewarded + events.size,
                totalRewardAmount = existing.totalRewardAmount + rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts + listOfNotNull(existing.totalImpact)),
            )
        } else {
            AppAllTimeActionSummary(
                version = 1,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                user = receiverId,
                appId = appId,
                actionsRewarded = events.size.toLong(),
                totalRewardAmount = rewardAmountIncrease,
                totalImpact = accumulateImpacts(impacts),
            )
        }
    }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, AppAllTimeActionSummary>,
    ): AppAllTimeActionSummary? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
