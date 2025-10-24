package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigInteger
import kotlin.collections.iterator
import kotlin.collections.plusAssign
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger

@Profile("stargate", "vtho-claimed-by-block")
@Service
open class VthoClaimedByBlockService(private val repository: VthoClaimedByBlockRepository) {
    /**
     * Builds per-block cumulative records from the provided events.
     *
     * Invariants / Fail-fast rules:
     * - If there is a latest persisted record, throw if ANY incoming event has blockNumber <=
     *   latestRecord.blockNumber (reprocessing or regression).
     *
     * Processing:
     * - Group events by blockNumber (ascending).
     * - Sum each block's required "value" (throws immediately if missing).
     * - Accumulate totals starting from the latest persisted total (or zero).
     * - Use the first event in each block as representative for blockId/timestamp, assuming they
     *   are identical within a block.
     */
    open fun processEvents(events: List<IndexedEvent>): List<VthoClaimedByBlock> {
        if (events.isEmpty()) return emptyList()

        val latestRecord = repository.getLatestRecord()
        val lastPersistedBlockNumber = latestRecord?.blockNumber

        // Fail fast on same/earlier block numbers than the last persisted block
        if (lastPersistedBlockNumber != null) {
            val offending =
                events
                    .asSequence()
                    .map { it.blockNumber }
                    .filter { it <= lastPersistedBlockNumber }
                    .minOrNull()
            if (offending != null) {
                throw IllegalStateException(
                    "Provided events include blockNumber $offending which is <= last persisted blockNumber $lastPersistedBlockNumber"
                )
            }
        }

        val startingTotal = latestRecord?.total ?: BigInteger.ZERO

        // Group by block and process in ascending order to build the cumulative total
        val eventsByBlock = events.groupBy { it.blockNumber }.toSortedMap()

        var runningTotal = startingTotal
        val output = mutableListOf<VthoClaimedByBlock>()

        for ((blockNumber, blockEvents) in eventsByBlock) {
            // Summing throws immediately if a 'value' is missing for any event in the block
            val blockSum =
                blockEvents.fold(BigInteger.ZERO) { acc, e ->
                    acc + e.requireValue() // throws if missing
                }
            runningTotal += blockSum

            // All events in a block share the same timestamp; first is fine
            val representative = blockEvents.first()

            output +=
                VthoClaimedByBlock(
                    blockId = representative.blockId,
                    blockNumber = blockNumber,
                    blockTimestamp = representative.blockTimestamp,
                    total = runningTotal,
                )
        }

        return output
    }

    open fun saveRecords(record: List<VthoClaimedByBlock>) {
        repository.saveAll(record)
    }
}

/**
 * Helper that enforces presence of the required "value" param and throws with context if missing.
 */
private fun IndexedEvent.requireValue(): BigInteger =
    this.params.getAsBigInteger("value")
        ?: throw IllegalStateException(
            "Event for block $blockNumber (blockId=$blockId) is missing required 'value' parameter"
        )
