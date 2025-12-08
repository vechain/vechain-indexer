package org.vechain.indexer.stargate.vetDelegated

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.RolloverUtils

/**
 * @title VetDelegatedByBlockService
 * @notice Indexes all VET delegation and withdrawal events into per-block cumulative snapshots.
 * @dev Responsibilities:
 *     - Enforces strictly increasing block order (no duplicates or rewinds).
 *     - Applies delegation/withdrawal deltas to: • global delegated VET total • per-level delegated
 *       totals (TokenLevel)
 *     - Supports missing levelId on devnet (treated as null).
 *     - Uses `RolloverUtils` for day/week/month/year rollover tracking.
 *     - Emits one `VetDelegatedByBlock` document per processed block.
 *
 * Expected params:
 *     - `amount` : BigInteger delegated or withdrawn.
 * - `levelId` : TokenLevel ordinal (may be absent on early devnet blocks).
 *
 * Supported event types:
 *     - DelegationInitiated
 * - DelegationWithdrawn
 */
@Profile("stargate", "vet-delegated-by-block")
@Service
open class VetDelegatedByBlockService(private val repository: VetDelegatedByBlockRepository) {
    /**
     * @param events Collection of decoded events sorted arbitrarily.
     * @return Ordered list of `VetDelegatedByBlock` output snapshots.
     * @notice Process all delegation-related events and convert them into cumulative per-block
     *   output entries.
     * @dev For each block:
     *     1. Compute total + per-level deltas.
     *     2. Update cumulative state.
     *     3. Apply rollover (day/week/month/year).
     *     4. Generate a `VetDelegatedByBlock` snapshot.
     *
     *    Enforces:
     *     - ascending block order
     *     - required `amount` field
     *     - optional `levelId` (null allowed on devnet)
     */
    open fun processEvents(events: List<IndexedEvent>): List<VetDelegatedByBlock> {
        if (events.isEmpty()) return emptyList()

        val latest = repository.getLatestRecord()
        val lastBlock = latest?.blockNumber

        if (lastBlock != null && events.any { it.blockNumber <= lastBlock }) {
            throw IllegalStateException("Events include block ≤ last persisted block $lastBlock")
        }

        var runningTotal = latest?.total ?: BigInteger.ZERO
        val runningByLevel =
            latest?.byLevel?.toMutableMap() ?: mutableMapOf<TokenLevel, BigInteger>()
        var runningNftCount = latest?.totalNftCount ?: 0L
        val runningNftCountByLevel =
            latest?.nftCountByLevel?.toMutableMap() ?: mutableMapOf<TokenLevel, Long>()

        val grouped = events.groupBy { it.blockNumber }.toSortedMap()
        val output = mutableListOf<VetDelegatedByBlock>()
        var prev: VetDelegatedByBlock? = latest

        for ((blockNum, blockEvents) in grouped) {
            var blockDelta = BigInteger.ZERO
            val perLevelDelta = mutableMapOf<TokenLevel, BigInteger>()

            blockEvents.forEach { evt ->
                val amount = evt.requireAmount()
                val level = evt.safeLevel()

                when (evt.eventType) {
                    "DelegationInitiated" -> {
                        runningTotal += amount
                        runningNftCount += 1
                        if (level != null) {
                            runningByLevel[level] =
                                (runningByLevel[level] ?: BigInteger.ZERO) + amount
                            perLevelDelta[level] =
                                (perLevelDelta[level] ?: BigInteger.ZERO) + amount
                            runningNftCountByLevel[level] =
                                (runningNftCountByLevel[level] ?: 0L) + 1
                        }
                        blockDelta += amount
                    }

                    "DelegationWithdrawn" -> {
                        runningTotal -= amount
                        runningNftCount -= 1
                        if (level != null) {
                            runningByLevel[level] =
                                (runningByLevel[level] ?: BigInteger.ZERO) - amount
                            perLevelDelta[level] =
                                (perLevelDelta[level] ?: BigInteger.ZERO) - amount
                            runningNftCountByLevel[level] =
                                (runningNftCountByLevel[level] ?: 0L) - 1
                        }
                        blockDelta -= amount
                    }

                    else -> throw IllegalArgumentException("Unknown eventType: ${evt.eventType}")
                }
            }

            val rep = blockEvents.first()

            val roll =
                RolloverUtils.calculateRollover(
                    blockTimestamp = rep.blockTimestamp,
                    delta = blockDelta,
                    ctx =
                        RolloverUtils.Context(
                            prevHourTotal = prev?.hourTotal ?: BigInteger.ZERO,
                            prevDayTotal = prev?.dayTotal ?: BigInteger.ZERO,
                            prevWeekTotal = prev?.weekTotal ?: BigInteger.ZERO,
                            prevMonthTotal = prev?.monthTotal ?: BigInteger.ZERO,
                            prevYearTotal = prev?.yearTotal ?: BigInteger.ZERO,
                            prevHour = prev?.hourOfDay,
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
                VetDelegatedByBlock(
                    blockId = rep.blockId,
                    blockNumber = blockNum,
                    blockTimestamp = rep.blockTimestamp,
                    total = runningTotal,
                    byLevel = runningByLevel.toMap(),
                    totalNftCount = runningNftCount,
                    nftCountByLevel = runningNftCountByLevel.toMap(),
                    hourOfDay = roll.hour,
                    dayOfMonth = roll.day,
                    weekOfYear = roll.week,
                    month = roll.month,
                    year = roll.year,
                    timeFrames = emptyList(),
                    blockTotal = blockDelta,
                    hourTotal = roll.hourTotal,
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

    /**
     * @param records List of documents to store.
     * @notice Persist multiple delegation snapshots.
     */
    open fun saveRecords(records: List<VetDelegatedByBlock>) {
        repository.saveAll(records)
    }
}

/**
 * @notice Require the presence of `"amount"` in event parameters.
 * @dev Throws if missing.
 */
private fun IndexedEvent.requireAmount(): BigInteger =
    this.params.getAsBigInteger("amount")
        ?: throw IllegalStateException(
            "Event for block $blockNumber (blockId=$blockId) is missing required 'amount' parameter"
        )

/**
 * @notice Safely decode the TokenLevel for devnet events that may lack `levelId`.
 * @dev Returns null if missing; otherwise converts ordinal → TokenLevel.
 */
private fun IndexedEvent.safeLevel(): TokenLevel? {
    val id = this.params.getAsInt("levelId") ?: return null
    return TokenLevel.fromOrdinal(id)
}
