package org.vechain.indexer.stargate.vetDelegated

import java.math.BigInteger
import kotlin.collections.set
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt

@Profile("stargate", "vet-delegated-by-block")
@Service
open class VetDelegatedByBlockService(private val repository: VetDelegatedByBlockRepository) {
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
     * - The `"amount"` parameter is required; we throw when reading it if missing.
     * - The `"levelId"` parameter is required and must map to a valid TokenLevel.
     * - Any **unknown `eventType`** causes an immediate error.
     */
    open fun processEvents(events: List<IndexedEvent>): List<VetDelegatedByBlock> {
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

        val output = mutableListOf<VetDelegatedByBlock>()

        for ((blockNumber, blockEvents) in eventsByBlock) {
            // Apply all deltas for this block
            blockEvents.forEach { e ->
                val amount = e.requireAmount()

                // throws if missing
                @Suppress("ktlint:standard:max-line-length")
                val levelId =
                    e.params.getAsInt("levelId")
                        ?: 0 // TODO: Update this to not set value to 0 if missing, devnet event is
                // missing levelId up to a certain block
                val level = TokenLevel.Companion.fromOrdinal(levelId)

                when (e.eventType) {
                    "DelegationInitiated" -> {
                        runningTotal += amount
                        if (level != null) {
                            runningByLevel[level] =
                                (runningByLevel[level] ?: BigInteger.ZERO) + amount
                        }
                    }
                    "DelegationWithdrawn" -> {
                        runningTotal -= amount
                        if (level != null) {
                            runningByLevel[level] =
                                (runningByLevel[level] ?: BigInteger.ZERO) - amount
                        }
                    }
                    else -> {
                        throw IllegalArgumentException("Unknown eventType: ${e.eventType}")
                    }
                }
            }

            // Snapshot after processing this block
            val representative = blockEvents.first()
            output +=
                VetDelegatedByBlock(
                    blockId = representative.blockId,
                    blockNumber = blockNumber,
                    blockTimestamp = representative.blockTimestamp,
                    total = runningTotal,
                    byLevel = runningByLevel.toMutableMap(), // snapshot
                )
        }

        return output
    }

    open fun saveRecords(records: List<VetDelegatedByBlock>) {
        repository.saveAll(records)
    }
}

/**
 * Helper that enforces presence of the required "amount" param and throws with context if missing.
 */
private fun IndexedEvent.requireAmount(): BigInteger =
    this.params.getAsBigInteger("amount")
        ?: throw IllegalStateException(
            "Event for block $blockNumber (blockId=$blockId) is missing required 'amount' parameter"
        )
