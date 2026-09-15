package org.vechain.indexer.b3tr.action

import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.accumulateImpacts
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getAction
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.getCycle
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.validateAndFilterImpacts
import org.vechain.indexer.b3tr.round.B3trRoundService
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.BlockUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock

/**
 * Rolls every `B3TR_ActionReward` into the six summaries, each new row built on the row before it.
 * The round an action belongs to is tracked across entries: restored from the Emissions contract on
 * the first one, then advanced by each `EmissionDistributed`.
 */
@Profile("b3tr", "b3tr-actions")
@Service
open class ActionSummaryService(
    private val repository: ActionWriteRepository,
    private val b3trRoundService: B3trRoundService,
    private val impactConfig: ActionImpactConfig,
) {
    private var currentRound: Int? = null

    /** One block's rewards that belong to one period. */
    private class Slice(
        val block: BlockDetails,
        val period: ActionPeriod,
        val actions: List<Action>,
    )

    /**
     * The newest row of each key over one period: what the schema holds, then what the entry adds.
     */
    private class Ledger {
        val entities = mutableMapOf<Pair<EntityType, String>, EntityActionSummary>()
        val appUsers = mutableMapOf<Pair<String, String>, AppUserActionSummary>()
    }

    private class Rows {
        val entities = mutableListOf<EntityActionSummary>()
        val appUsers = mutableListOf<AppUserActionSummary>()
    }

    open suspend fun processEvents(events: List<IndexedEvent>): ActionSummaryUpdate {
        assertEventTypes(events, ACTION_REWARD, *ROUND_TRANSITIONS)
        var round = currentRound ?: restoreRound(events.first().blockNumber)

        val slices = mutableListOf<Slice>()
        groupByBlock(events).forEach { (block, blockEvents) ->
            val byRound = LinkedHashMap<Int, MutableList<Action>>()
            for (event in blockEvents) {
                if (event.eventType != ACTION_REWARD) {
                    round = advance(round, event)
                    continue
                }
                check(round > 0) {
                    "$NAME cannot attribute events at block ${block.blockNumber} to a round: " +
                        "roundId is still 0 (no EmissionDistributed event observed). " +
                        "Check the indexer start-block aligns with the Emissions contract deployment."
                }
                byRound.getOrPut(round) { mutableListOf() } += normalised(getAction(event))
            }
            if (byRound.isEmpty()) return@forEach
            val actions = byRound.values.flatten()
            slices += Slice(block, ActionPeriod.AllTime, actions)
            slices +=
                Slice(
                    block,
                    ActionPeriod.Day(BlockUtils.getDateAtUTC(block.blockTimestamp)),
                    actions,
                )
            byRound.forEach { (roundId, rewards) ->
                slices += Slice(block, ActionPeriod.Round(roundId), rewards)
            }
        }

        val ledgers =
            slices
                .groupBy { it.period }
                .mapValues { (period, of) -> load(period, of.flatMap { it.actions }) }
        val rows = Rows()
        slices.forEach { apply(it, ledgers.getValue(it.period), rows) }

        currentRound = round
        return ActionSummaryUpdate(rows.entities, rows.appUsers)
    }

    open fun invalidateRuntimeState() {
        currentRound = null
    }

    /** Null means Emissions was not yet deployed: round 0 until an EmissionDistributed arrives. */
    private suspend fun restoreRound(firstBlock: Long): Int =
        b3trRoundService.getCurrentRound(BlockRevision.Number((firstBlock - 1).coerceAtLeast(0)))
            ?: 0

    private fun advance(round: Int, transition: IndexedEvent): Int {
        val next = getCycle(transition)
        check(next == round + 1) {
            "$NAME received unexpected ${transition.eventType} cycle $next at block " +
                "${transition.blockNumber} (expected ${round + 1}). Rounds must advance by exactly 1."
        }
        return next
    }

    /** Keys as the schema reads them back, so the entry's rows chain onto the stored ones. */
    private fun normalised(action: Action): Action =
        action.copy(
            receiver = HexUtils.normalise(action.receiver),
            appId = HexUtils.normalise(action.appId),
        )

    private fun load(period: ActionPeriod, actions: List<Action>): Ledger {
        val ledger = Ledger()
        val entities =
            actions.map { EntityType.USER to it.receiver } +
                actions.map { EntityType.APP to it.appId } +
                (EntityType.GLOBAL to EntityType.GLOBAL.name)
        repository.findCurrentEntities(period, entities.toSet()).forEach {
            ledger.entities[it.entityType to it.entity] = it
        }
        repository
            .findCurrentAppUsers(period, actions.map { it.appId to it.receiver }.toSet())
            .forEach { ledger.appUsers[it.appId to it.user] = it }
        return ledger
    }

    /** A wallet new to an app, or to the period, is counted on the app's row or the GLOBAL one. */
    private fun apply(slice: Slice, ledger: Ledger, rows: Rows) {
        val block = slice.block
        val newPairs = mutableMapOf<String, Long>()
        slice.actions
            .groupBy { it.appId to it.receiver }
            .forEach { (pair, rewards) ->
                val before = ledger.appUsers[pair]
                if (before == null) newPairs.merge(pair.first, 1L, Long::plus)
                val row =
                    AppUserActionSummary(
                        appId = pair.first,
                        user = pair.second,
                        period = slice.period,
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        actionsRewarded = (before?.actionsRewarded ?: 0) + rewards.size,
                        totalRewardAmount =
                            (before?.totalRewardAmount ?: BigDecimal.ZERO) +
                                rewards.sumOf { it.amount },
                        totalImpact = impact(rewards, before?.totalImpact),
                    )
                ledger.appUsers[pair] = row
                rows.appUsers += row
            }

        var newUsers = 0L
        slice.actions
            .groupBy { it.receiver }
            .forEach { (user, rewards) ->
                if (ledger.entities[EntityType.USER to user] == null) newUsers++
                entity(slice, ledger, rows, EntityType.USER, user, rewards, 0)
            }
        slice.actions
            .groupBy { it.appId }
            .forEach { (app, rewards) ->
                entity(slice, ledger, rows, EntityType.APP, app, rewards, newPairs[app] ?: 0)
            }
        entity(
            slice,
            ledger,
            rows,
            EntityType.GLOBAL,
            EntityType.GLOBAL.name,
            slice.actions,
            newUsers,
        )
    }

    private fun entity(
        slice: Slice,
        ledger: Ledger,
        rows: Rows,
        entityType: EntityType,
        entity: String,
        rewards: List<Action>,
        newUsers: Long,
    ) {
        val before = ledger.entities[entityType to entity]
        val row =
            EntityActionSummary(
                entityType = entityType,
                entity = entity,
                period = slice.period,
                blockId = slice.block.blockId,
                blockNumber = slice.block.blockNumber,
                blockTimestamp = slice.block.blockTimestamp,
                actionsRewarded = (before?.actionsRewarded ?: 0) + rewards.size,
                totalRewardAmount =
                    (before?.totalRewardAmount ?: BigDecimal.ZERO) + rewards.sumOf { it.amount },
                totalImpact = impact(rewards, before?.totalImpact),
                uniqueUsers = (before?.uniqueUsers ?: 0) + newUsers,
            )
        ledger.entities[entityType to entity] = row
        rows.entities += row
    }

    private fun impact(rewards: List<Action>, before: Impact?): Impact? =
        accumulateImpacts(
            validateAndFilterImpacts(rewards.mapNotNull { it.proof?.impact }, impactConfig) +
                listOfNotNull(before)
        )

    companion object {
        private const val ACTION_REWARD = "B3TR_ActionReward"
        private val ROUND_TRANSITIONS = arrayOf("EmissionDistributed", "EmissionDistributedV2")
        private const val NAME = IndexerNames.B3TR_ACTION.NAME
    }
}
