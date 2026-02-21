package org.vechain.indexer.stargate.vetDelegated

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.RolloverUtils
import org.vechain.indexer.validator.DelegationRepository

/**
 * @title VetDelegatedByBlockService
 * @notice Indexes VET delegation state by aggregating the delegations collection.
 * @dev Responsibilities:
 *     - Runs aggregation queries on the delegations collection to get current state
 *     - Tracks active VET (ACTIVE + EXITING delegations) with per-level breakdown
 *     - Uses `RolloverUtils` for day/week/month/year rollover tracking on active VET
 *     - Emits one `VetDelegatedByBlock` document per processed block
 */
@Profile("stargate", "vet-delegated-by-block")
@Service
open class VetDelegatedByBlockService(
    private val repository: VetDelegatedByBlockRepository,
    private val delegationRepository: DelegationRepository,
) {
    private var latestRecordCache: VetDelegatedByBlock? = null
    private var cacheInitialized: Boolean = false

    /**
     * @param block The block to process.
     * @return List of `VetDelegatedByBlock` output snapshots (includes rollover if applicable).
     * @notice Process a block by aggregating current delegation state.
     * @dev Steps:
     *     1. Run aggregation queries on delegations collection
     *     2. Compute active (ACTIVE + EXITING) totals
     *     3. Calculate delta from previous snapshot for time-frame rollover
     *     4. Generate a `VetDelegatedByBlock` snapshot
     */
    open fun processBlock(block: Block): List<VetDelegatedByBlock> {
        val latest =
            if (cacheInitialized) latestRecordCache
            else {
                val loaded = repository.getLatestRecord()
                latestRecordCache = loaded
                cacheInitialized = true
                loaded
            }
        val lastBlock = latest?.blockNumber

        if (lastBlock != null && block.number <= lastBlock) {
            throw IllegalStateException("Block ${block.number} ≤ last persisted block $lastBlock")
        }

        // Aggregate current delegation state (ACTIVE + EXITING)
        val activeByLevelResults = delegationRepository.aggregateActiveDelegationsByLevel()

        // Build active totals from aggregation
        var total = BigInteger.ZERO
        val activeByLevel = mutableMapOf<TokenLevel, BigInteger>()
        var totalNftCount = 0L
        val activeNftCountByLevel = mutableMapOf<TokenLevel, Long>()

        for (result in activeByLevelResults) {
            val level = TokenLevel.valueOf(result.level)
            val amount = BigInteger(result.totalWei)
            activeByLevel[level] = amount
            activeNftCountByLevel[level] = result.nftCount
            total += amount
            totalNftCount += result.nftCount
        }

        val byLevel = activeByLevel.toMap()
        val nftCountByLevel = activeNftCountByLevel.toMap()

        // Calculate delta for time-frame rollover
        val prevTotal = latest?.total ?: BigInteger.ZERO
        val delta = total - prevTotal

        val output = mutableListOf<VetDelegatedByBlock>()

        val roll =
            RolloverUtils.calculateRollover(
                blockTimestamp = block.timestamp,
                delta = delta,
                ctx =
                    RolloverUtils.Context(
                        prevHourTotal = latest?.hourTotal ?: BigInteger.ZERO,
                        prevDayTotal = latest?.dayTotal ?: BigInteger.ZERO,
                        prevWeekTotal = latest?.weekTotal ?: BigInteger.ZERO,
                        prevMonthTotal = latest?.monthTotal ?: BigInteger.ZERO,
                        prevYearTotal = latest?.yearTotal ?: BigInteger.ZERO,
                        prevHour = latest?.hourOfDay,
                        prevDay = latest?.dayOfMonth,
                        prevWeek = latest?.weekOfYear,
                        prevMonth = latest?.month,
                        prevYear = latest?.year,
                    ),
            )

        // Only save if there's an actual change or this is the first record
        val hasChange = delta != BigInteger.ZERO
        val isFirstRecord = latest == null
        val hasRollover = roll.timeFrames.isNotEmpty()

        // If no change and not first record and no rollover, skip saving
        if (!hasChange && !isFirstRecord && !hasRollover) {
            return output
        }

        // If there's a time-frame rollover, emit previous record with timeFrames set
        if (hasRollover && latest != null) {
            output += latest.copy(timeFrames = roll.timeFrames)
        }

        val doc =
            VetDelegatedByBlock(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                total = total,
                byLevel = byLevel,
                totalNftCount = totalNftCount,
                nftCountByLevel = nftCountByLevel,
                hourOfDay = roll.hour,
                dayOfMonth = roll.day,
                weekOfYear = roll.week,
                month = roll.month,
                year = roll.year,
                timeFrames = emptyList(),
                blockTotal = delta,
                hourTotal = roll.hourTotal,
                dayTotal = roll.dayTotal,
                weekTotal = roll.weekTotal,
                monthTotal = roll.monthTotal,
                yearTotal = roll.yearTotal,
            )

        output += doc

        return output
    }

    /**
     * @param records List of documents to store.
     * @notice Persist multiple delegation snapshots.
     */
    open fun saveRecords(records: List<VetDelegatedByBlock>) {
        repository.saveAll(records)
        if (records.isNotEmpty()) {
            latestRecordCache = records.last()
        }
    }

    open fun invalidateCache() {
        latestRecordCache = null
        cacheInitialized = false
    }
}
