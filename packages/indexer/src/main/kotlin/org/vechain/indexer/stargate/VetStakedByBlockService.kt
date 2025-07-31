package org.vechain.indexer.stargate

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt

@Profile("stargate", "vet-staked-by-block")
@Service
open class VetStakedByBlockService(private val repository: VetStakedByBlockRepository) {

    /**
     * Build per-block cumulative records from the provided events.
     *
     * Behavior:
     * - Loads the latest persisted record to get the last processed blockNumber and running totals.
     * - FAILS FAST if any incoming event has blockNumber <= last persisted blockNumber.
     * - Groups events by blockNumber (ascending).
     * - For each block, applies all stake/unstake deltas, updating:
     *     - cumulative `total`
     *     - cumulative `byLevel` map
     * - Returns a list ordered by ascending blockNumber. Each element contains a snapshot of the
     *   cumulative totals after processing that block.
     *
     * Assumptions / Optimizations:
     * - All events in the same block share the same `blockTimestamp`. We use the first event as the
     *   representative for `blockId` and `blockTimestamp`.
     * - The `"value"` parameter is required; we throw when reading it if missing.
     * - The `"levelId"` parameter is required and must map to a valid TokenLevel.
     * - Any **unknown `eventType`** causes an immediate error.
     */
    open fun processEvents(events: List<IndexedEvent>): List<VetStakedByBlock> {
        if (events.isEmpty()) return emptyList()

        val latestRecord = repository.getLatestRecord()
        val lastPersistedBlockNumber = latestRecord?.blockNumber

        // Fail fast if any incoming block is the same or earlier than the last persisted block.
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

        var runningTotal = latestRecord?.total ?: BigInteger.ZERO
        val runningByLevel: MutableMap<TokenLevel, BigInteger> =
            (latestRecord?.byLevel?.toMutableMap() ?: mutableMapOf())

        // Group by block and process in ascending order
        val eventsByBlock = events.groupBy { it.blockNumber }.toSortedMap()

        val output = mutableListOf<VetStakedByBlock>()

        for ((blockNumber, blockEvents) in eventsByBlock) {
            // Apply all deltas for this block
            blockEvents.forEach { e ->
                val amount = e.requireValue() // throws if missing
                val level = e.requireLevel() // throws if missing or invalid

                when (e.eventType) {
                    "STARGATE_STAKE" -> {
                        runningTotal += amount
                        runningByLevel[level] = (runningByLevel[level] ?: BigInteger.ZERO) + amount
                    }
                    "STARGATE_UNSTAKE" -> {
                        runningTotal -= amount
                        runningByLevel[level] = (runningByLevel[level] ?: BigInteger.ZERO) - amount
                    }
                    else -> {
                        // NEW: fail fast on unknown event type
                        throw IllegalArgumentException("Unknown eventType: ${e.eventType}")
                    }
                }
            }

            // Snapshot after processing this block
            val representative = blockEvents.first()
            output +=
                VetStakedByBlock(
                    blockId = representative.blockId,
                    blockNumber = blockNumber,
                    blockTimestamp = representative.blockTimestamp,
                    total = runningTotal,
                    byLevel = runningByLevel.toMutableMap(), // snapshot
                )
        }

        return output
    }

    open fun saveRecord(record: VetStakedByBlock) {
        repository.save(record)
    }

    open fun saveRecords(records: List<VetStakedByBlock>) {
        repository.saveAll(records)
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

/** Helper that enforces presence/validity of levelId and returns the TokenLevel. */
private fun IndexedEvent.requireLevel(): TokenLevel {
    val levelId =
        this.params.getAsInt("levelId")
            ?: throw IllegalArgumentException("Missing levelId in event params")
    return TokenLevel.fromOrdinal(levelId)
        ?: throw IllegalArgumentException("Invalid levelId: $levelId")
}
