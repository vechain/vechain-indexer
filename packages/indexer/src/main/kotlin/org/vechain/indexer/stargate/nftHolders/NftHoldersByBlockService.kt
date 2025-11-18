package org.vechain.indexer.stargate.nftHolders

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.RolloverUtils

@Profile("stargate", "nft-holders-by-block")
@Service
open class NftHoldersByBlockService(private val repository: NftHoldersByBlockRepository) {
    /**
     * @param events The decoded on-chain events grouped across arbitrary blocks.
     * @return A list of `NftHoldersByBlock` documents in ascending block order.
     * @notice Process raw NFT stake/unstake events across multiple blocks.
     * @dev Per-block flow:
     *     1. Compute per-block delta (total + per level).
     *     2. Update running totals from previous state.
     *     3. Apply rollover logic relative to *the immediate previous block*.
     *     4. If rollover occurred, emit an updated copy of the previous row.
     *     5. Emit the new row for the current block.
     *
     *    State tracking:
     *         - `prev` always refers to the most recently emitted record (or the DB record if this
     *           is the first block).
     *
     *   Strict ordering:
     *         - Throws if any event's block number ≤ last stored block number.
     */
    open fun processEvents(events: List<IndexedEvent>): List<NftHoldersByBlock> {
        if (events.isEmpty()) return emptyList()

        val latest = repository.getLatestRecord()
        val lastBlock = latest?.blockNumber

        // Enforce strict ascending block ordering
        if (lastBlock != null && events.any { it.blockNumber <= lastBlock }) {
            throw IllegalStateException("Events include block ≤ last persisted block $lastBlock")
        }

        var runningTotal = latest?.total ?: 0L
        val runningByLevel = latest?.byLevel?.toMutableMap() ?: mutableMapOf<TokenLevel, Long>()

        val grouped = events.groupBy { it.blockNumber }.toSortedMap()
        val output = mutableListOf<NftHoldersByBlock>()
        var prev: NftHoldersByBlock? = latest

        for ((blockNum, blockEvents) in grouped) {
            var blockDelta = 0L

            blockEvents.forEach { evt ->
                val level = evt.requireLevel()

                when (evt.eventType) {
                    "STARGATE_STAKE" -> {
                        runningTotal += 1
                        runningByLevel[level] = (runningByLevel[level] ?: 0L) + 1
                        blockDelta += 1
                    }

                    "STARGATE_UNSTAKE" -> {
                        runningTotal -= 1
                        runningByLevel[level] = (runningByLevel[level] ?: 0L) - 1
                        blockDelta -= 1
                    }

                    else -> throw IllegalArgumentException("Unknown eventType: ${evt.eventType}")
                }
            }

            val rep = blockEvents.first()

            val roll =
                RolloverUtils.calculateRollover(
                    blockTimestamp = rep.blockTimestamp,
                    delta = BigInteger.valueOf(blockDelta), // convert delta → BigInteger
                    ctx =
                        RolloverUtils.Context(
                            prevDayTotal = prev?.dayTotal ?: BigInteger.ZERO,
                            prevWeekTotal = prev?.weekTotal ?: BigInteger.ZERO,
                            prevMonthTotal = prev?.monthTotal ?: BigInteger.ZERO,
                            prevYearTotal = prev?.yearTotal ?: BigInteger.ZERO,
                            prevDay = prev?.dayOfMonth,
                            prevWeek = prev?.weekOfYear,
                            prevMonth = prev?.month,
                            prevYear = prev?.year,
                        ),
                )

            if (roll.timeFrames.isNotEmpty() && prev != null) {
                output += prev.copy(timeFrames = roll.timeFrames)
            }

            val doc =
                NftHoldersByBlock(
                    blockId = rep.blockId,
                    blockNumber = blockNum,
                    blockTimestamp = rep.blockTimestamp,
                    total = runningTotal,
                    byLevel = runningByLevel.toMap(),
                    dayOfMonth = roll.day,
                    weekOfYear = roll.week,
                    month = roll.month,
                    year = roll.year,
                    timeFrames = emptyList(),
                    blockTotal = BigInteger.valueOf(blockDelta),
                    dayTotal = roll.dayTotal,
                    weekTotal = roll.weekTotal,
                    monthTotal = roll.monthTotal,
                    yearTotal = roll.yearTotal,
                )

            output += doc
            prev = doc
        }

        return output
    }

    /** @notice Persist a single per-block NFT holder statistics record. */
    open fun saveRecord(record: NftHoldersByBlock) {
        repository.save(record)
    }

    /** @notice Persist multiple per-block NFT holder statistics records. */
    open fun saveRecords(records: List<NftHoldersByBlock>) {
        repository.saveAll(records)
    }
}

/** Require TokenLevel */
private fun IndexedEvent.requireLevel(): TokenLevel {
    val id =
        this.params.getAsInt("levelId")
            ?: throw IllegalArgumentException("Missing levelId in event params")

    return TokenLevel.fromOrdinal(id) ?: throw IllegalArgumentException("Invalid levelId: $id")
}
