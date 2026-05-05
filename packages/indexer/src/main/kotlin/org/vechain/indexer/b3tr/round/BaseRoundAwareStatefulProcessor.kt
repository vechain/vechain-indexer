package org.vechain.indexer.b3tr.round

import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.b3tr.action.ActionSummaryUtils
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.BlockRevision

/**
 * Base class for processors that maintain a current B3TR round id across blocks.
 *
 * Round id is resolved lazily on the first non-empty batch by querying
 * [B3trRoundService.getCurrentRound] at the block immediately preceding the first incoming event.
 * If the contract call reverts (e.g. the indexer started before the `Emissions` contract was
 * deployed) the processor defaults to round 0 and waits for an `EmissionDistributed` event to
 * advance it. Receiving a non-transition event while still at round 0 fails fast.
 *
 * Within each batch, events are sliced at `EmissionDistributed`/`EmissionDistributedV2` boundaries
 * so that each call to [processSlice] receives only events that belong to a single round.
 */
abstract class BaseRoundAwareStatefulProcessor<T : VersionedDocument>(
    repository: BaseIndexedRepository<*, *>,
    mongoTemplate: MongoTemplate,
    indexerName: String,
    checkpointService: CheckpointService,
    collectionName: String,
    processorMetrics: ProcessorMetrics,
    private val b3trRoundService: B3trRoundService,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = indexerName,
        checkpointService = checkpointService,
        collectionName = collectionName,
        processorMetrics = processorMetrics,
    ) {

    protected var roundId: Int? = null
        private set

    final override suspend fun processEntry(entry: IndexingResult) {
        val events = entry.events()
        if (events.isEmpty()) return

        val initialRound = roundId ?: resolveInitialRound(events.first().blockNumber)
        val sliced = sliceByRound(events, initialRound)

        val updated = mutableListOf<T>()
        val existing = mutableListOf<T>()
        for (slice in sliced.slices) {
            check(slice.roundId > 0) {
                "$indexerName cannot attribute events at block ${slice.events.first().blockNumber} " +
                    "to a round: roundId is still 0 (no EmissionDistributed event observed). " +
                    "Check the indexer start-block aligns with the Emissions contract deployment."
            }
            val (u, e) = processSlice(slice.events, slice.roundId)
            updated += u
            existing += e
        }

        roundId = sliced.finalRoundId

        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            save(updated, existing)
        }
    }

    override fun rollback(blockNumber: Long) {
        super.rollback(blockNumber)
        roundId = null
    }

    /**
     * Process a contiguous run of business events that all belong to [roundId]. Called once per
     * round-slice within a batch. Returns `(updated, existing)` documents.
     */
    protected abstract fun processSlice(
        events: List<IndexedEvent>,
        roundId: Int,
    ): Pair<List<T>, List<T>>

    protected abstract fun save(updated: List<T>, existing: List<T>)

    private suspend fun resolveInitialRound(firstEventBlockNumber: Long): Int {
        val priorBlock = (firstEventBlockNumber - 1).coerceAtLeast(0L)
        // Null indicates the contract reverted (e.g. Emissions not yet deployed at this block).
        // Default to round 0 and rely on EmissionDistributed events to advance it.
        return b3trRoundService.getCurrentRound(BlockRevision.Number(priorBlock)) ?: 0
    }

    private data class RoundSlice(val roundId: Int, val events: List<IndexedEvent>)

    private data class SliceResult(val slices: List<RoundSlice>, val finalRoundId: Int)

    private fun sliceByRound(events: List<IndexedEvent>, startRoundId: Int): SliceResult {
        val slices = mutableListOf<RoundSlice>()
        val current = mutableListOf<IndexedEvent>()
        var currentRoundId = startRoundId

        for (event in events) {
            if (isRoundTransition(event)) {
                if (current.isNotEmpty()) {
                    slices.add(RoundSlice(currentRoundId, current.toList()))
                    current.clear()
                }
                val nextCycle = ActionSummaryUtils.getCycle(event)
                check(nextCycle == currentRoundId + 1) {
                    "$indexerName received unexpected ${event.eventType} cycle $nextCycle " +
                        "at block ${event.blockNumber} (expected ${currentRoundId + 1}). " +
                        "Rounds must advance by exactly 1."
                }
                currentRoundId = nextCycle
            } else {
                current.add(event)
            }
        }
        if (current.isNotEmpty()) {
            slices.add(RoundSlice(currentRoundId, current.toList()))
        }
        return SliceResult(slices, currentRoundId)
    }

    private fun isRoundTransition(event: IndexedEvent): Boolean =
        event.eventType == "EmissionDistributed" || event.eventType == "EmissionDistributedV2"
}
