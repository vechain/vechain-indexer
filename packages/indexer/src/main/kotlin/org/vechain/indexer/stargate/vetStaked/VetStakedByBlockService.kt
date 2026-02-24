package org.vechain.indexer.stargate.vetStaked

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.RolloverUtils

/**
 * @title VetStakedByBlockService
 * @notice Indexes all VET stake/unstake events and produces a cumulative snapshot per block.
 * @dev Responsibilities:
 *     - Enforces ascending block order (no duplicates or rewinds).
 *     - Applies stake/unstake deltas to: • global total staked VET • per-level staked totals
 *     - Calls `RolloverUtils` to maintain day/week/month/year rollovers.
 *     - Emits one `VetStakedByBlock` entry per processed block.
 *
 * Expected event parameters:
 *     - `value` : BigInteger amount of VET staked or unstaked.
 * - `levelId` : TokenLevel ordinal representing NFT level.
 *
 * Supported event types:
 *     - STARGATE_STAKE
 * - STARGATE_UNSTAKE
 */
@Profile("stargate", "vet-staked-by-block")
@Service
open class VetStakedByBlockService(private val repository: VetStakedByBlockRepository) {
    private var latestRecordCache: VetStakedByBlock? = null

    /**
     * @param events A list of decoded `IndexedEvent`s for multiple blocks.
     * @return Ordered list of `VetStakedByBlock` documents.
     * @notice Process all VET stake/unstake events and convert them into a per-block cumulative
     *   dataset.
     * @dev For each block:
     *     1. Calculate deltas (total + per-level).
     *     2. Update global running totals.
     *     3. Apply rollover logic for day/week/month/year.
     *     4. Produce `VetStakedByBlock` snapshot.
     *
     *    Enforces:
     *     - Strict ascending blockNumber.
     *     - Required params (`value`, `levelId`).
     */
    open fun processEvents(events: List<IndexedEvent>): List<VetStakedByBlock> {
        if (events.isEmpty()) return emptyList()

        val latest = latestRecordCache ?: repository.getLatestRecord()
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
        val output = mutableListOf<VetStakedByBlock>()
        var prev: VetStakedByBlock? = latest

        for ((blockNum, blockEvents) in grouped) {
            var blockDelta = BigInteger.ZERO
            val perLevelDelta = mutableMapOf<TokenLevel, BigInteger>()

            for (evt in blockEvents) {
                val amount = evt.requireValue()
                val level = evt.requireLevel()

                when (evt.eventType) {
                    "STARGATE_STAKE" -> {
                        runningTotal += amount
                        runningByLevel[level] = (runningByLevel[level] ?: BigInteger.ZERO) + amount
                        perLevelDelta[level] = (perLevelDelta[level] ?: BigInteger.ZERO) + amount
                        blockDelta += amount
                        runningNftCount += 1
                        runningNftCountByLevel[level] = (runningNftCountByLevel[level] ?: 0L) + 1
                    }

                    "STARGATE_UNSTAKE" -> {
                        runningTotal -= amount
                        runningByLevel[level] = (runningByLevel[level] ?: BigInteger.ZERO) - amount
                        perLevelDelta[level] = (perLevelDelta[level] ?: BigInteger.ZERO) - amount
                        blockDelta -= amount
                        runningNftCount -= 1
                        runningNftCountByLevel[level] = (runningNftCountByLevel[level] ?: 0L) - 1
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
                VetStakedByBlock(
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
     * @param records List of `VetStakedByBlock` entries.
     * @notice Persist a batch of per-block staking records.
     */
    @Transactional(rollbackFor = [Exception::class])
    open fun saveRecords(records: List<VetStakedByBlock>) {
        repository.saveAll(records)
        if (records.isNotEmpty()) {
            latestRecordCache = records.maxBy { it.blockNumber }
        }
    }
}

/**
 * @notice Require the presence of parameter `"value"` in an event.
 * @dev Throws with full context if missing.
 */
private fun IndexedEvent.requireValue(): BigInteger =
    this.params.getAsBigInteger("value")
        ?: throw IllegalStateException(
            "Event for block $blockNumber (blockId=$blockId) is missing required 'value'"
        )

/**
 * @notice Require and decode a valid `levelId` parameter.
 * @dev Converts `levelId` → TokenLevel or fails loudly.
 */
private fun IndexedEvent.requireLevel(): TokenLevel {
    val id =
        this.params.getAsInt("levelId")
            ?: throw IllegalArgumentException("Missing levelId in event params")

    return TokenLevel.fromOrdinal(id) ?: throw IllegalArgumentException("Invalid levelId: $id")
}
