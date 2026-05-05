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
 * [B3trRoundService.getCurrentRound] at the block immediately preceding the first incoming event,
 * giving the authoritative round at the resume point regardless of whether the latest record's
 * round is stale. The processor fails fast if the contract call cannot resolve a round.
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
        return b3trRoundService.getCurrentRound(BlockRevision.Number(priorBlock))
            ?: error(
                "Unable to resolve current B3TR round at block $priorBlock via Emissions.getCurrentCycle()"
            )
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
                currentRoundId = ActionSummaryUtils.getCycle(event)
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
