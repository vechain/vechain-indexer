package org.vechain.indexer.stargate.vetDelegated

import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.CacheUtils
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
    private val logger = LoggerFactory.getLogger(VetDelegatedByBlockService::class.java)
    private var latestRecordCache: VetDelegatedByBlock? = null

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
        val latest = validateAndLoadLatest(block)

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
            advanceCache(block, latest)
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
    @Transactional(rollbackFor = [Exception::class])
    open fun saveRecords(records: List<VetDelegatedByBlock>) {
        repository.saveAll(records)
        if (records.isNotEmpty()) {
            val latest = records.maxBy { it.blockNumber }
            CacheUtils.updateAfterCommit(
                latest,
                { latestRecordCache = it },
                { latestRecordCache = null },
            )
        }
    }

    private fun advanceCache(block: Block, latest: VetDelegatedByBlock) {
        latestRecordCache =
            latest.copy(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
            )
    }

    private fun validateAndLoadLatest(block: Block): VetDelegatedByBlock? {
        val latest = loadLatest(block) ?: return null

        if (block.number <= latest.blockNumber) {
            throw IllegalStateException(
                "Block ${block.number} is at or before last persisted block ${latest.blockNumber}"
            )
        }

        if (block.number > latest.blockNumber + 1) {
            logger.warn(
                "Forward gap detected: block {} is {} blocks ahead of last persisted block {}",
                block.number,
                block.number - latest.blockNumber,
                latest.blockNumber,
            )
        }

        return latest
    }

    private fun loadLatest(block: Block): VetDelegatedByBlock? {
        val cached = latestRecordCache
        if (
            cached != null &&
                cached.blockNumber == block.number - 1 &&
                cached.blockId == block.parentID
        ) {
            return cached
        }

        if (cached != null) {
            logger.info(
                "Cache miss for block {}: cached blockNumber={}, expected={}, parentID match={}",
                block.number,
                cached.blockNumber,
                block.number - 1,
                cached.blockId == block.parentID,
            )
        }

        return repository.getLatestRecord()
    }
}
