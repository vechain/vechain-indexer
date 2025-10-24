package org.vechain.indexer.stargate.nftHolders

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.ParamUtils.getAsInt

@Profile("stargate", "nft-holders-by-block")
@Service
open class NftHoldersByBlockService(private val repository: NftHoldersByBlockRepository) {
    /**
     * Build per-block cumulative records of NFT holders (total and by level).
     *
     * Behavior:
     * - Loads the latest persisted record for the last processed blockNumber and running totals.
     * - FAILS FAST if any incoming event has blockNumber <= last persisted blockNumber.
     * - Groups events by blockNumber (ascending).
     * - For each block, applies all stake/unstake deltas, updating:
     *     - cumulative `total` (Long)
     *     - cumulative `byLevel` (MutableMap<TokenLevel, Long>)
     * - Returns a list ordered by ascending blockNumber. Each element is a snapshot of the
     *   cumulative totals after processing that block.
     *
     * Assumptions / Optimizations:
     * - All events in the same block share the same `blockTimestamp`. Use the first event as
     *   representative for `blockId` and `blockTimestamp`.
     * - `"levelId"` is required and must map to a valid TokenLevel; we throw on access if
     *   missing/invalid.
     * - Any unknown `eventType` causes an immediate error.
     */
    open fun processEvents(events: List<IndexedEvent>): List<NftHoldersByBlock> {
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

        var runningTotal = latestRecord?.total ?: 0L
        val runningByLevel: MutableMap<TokenLevel, Long> =
            (latestRecord?.byLevel?.toMutableMap() ?: mutableMapOf())

        // Group by block and process in ascending order
        val eventsByBlock = events.groupBy { it.blockNumber }.toSortedMap()

        val output = mutableListOf<NftHoldersByBlock>()

        for ((blockNumber, blockEvents) in eventsByBlock) {
            // Apply all deltas for this block
            blockEvents.forEach { e ->
                val level = e.requireLevel() // throws if missing or invalid

                when (e.eventType) {
                    "STARGATE_STAKE" -> {
                        runningTotal += 1L
                        runningByLevel[level] = (runningByLevel[level] ?: 0L) + 1L
                    }
                    "STARGATE_UNSTAKE" -> {
                        runningTotal -= 1L
                        runningByLevel[level] = (runningByLevel[level] ?: 0L) - 1L
                    }
                    else -> {
                        throw IllegalArgumentException("Unknown eventType: ${e.eventType}")
                    }
                }
            }

            // Snapshot after processing this block
            val representative = blockEvents.first()
            output +=
                NftHoldersByBlock(
                    blockId = representative.blockId,
                    blockNumber = blockNumber,
                    blockTimestamp = representative.blockTimestamp,
                    total = runningTotal,
                    byLevel = runningByLevel.toMutableMap(), // snapshot copy
                )
        }

        return output
    }

    open fun saveRecord(record: NftHoldersByBlock) {
        repository.save(record)
    }

    open fun saveRecords(records: List<NftHoldersByBlock>) {
        repository.saveAll(records)
    }
}

/** Helper that enforces presence/validity of levelId and returns the TokenLevel. */
private fun IndexedEvent.requireLevel(): TokenLevel {
    val levelId =
        this.params.getAsInt("levelId")
            ?: throw IllegalArgumentException("Missing levelId in event params")
    return TokenLevel.fromOrdinal(levelId)
        ?: throw IllegalArgumentException("Invalid levelId: $levelId")
}
