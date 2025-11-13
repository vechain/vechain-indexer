package org.vechain.indexer.stargate.vthoGenerated

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.RolloverUtils
import org.vechain.indexer.validator.domain.ValidatorDecoder.hasAbiData

@Profile("stargate", "vtho-generated-by-block")
@Service
open class VthoGeneratedByBlockService(private val repository: VthoGeneratedByBlockRepository) {
    /**
     * @param events List of decoded blockchain events for the block.
     * @param block Block metadata (timestamp, number, id).
     * @param callResponses ABI call responses containing VTHO balance.
     * @return A fully populated `VthoGeneratedByBlock` record, or null when nothing changed.
     * @notice Process a single block and compute updated VTHO generation totals.
     * @dev
     *     - Returns `null` if the block contains no useful information.
     *     - Enforces monotonic block-ordering (fails on reorg-style backwards input).
     *     - Aggregates: ▪ on-chain balanceOf() result ▪ event-derived reward deltas
     *     - Applies day/week/month/year rollover logic using `RolloverUtils`.
     */
    open fun processBlock(
        events: List<IndexedEvent>,
        block: Block,
        callResponses: List<InspectionResult>,
    ): VthoGeneratedByBlock? {
        // Skip blocks with nothing to index
        if (events.isEmpty() && !callResponses[0].hasAbiData()) return null

        // Load previous entry & validate ordering
        val latest = validateAndLoadLatest(block)

        // Compute this block's totals (claimed + total + delta)
        val totals = calculateTotalsForBlock(events, callResponses, latest)
        if (totals.blockTotal == BigInteger.ZERO) return null

        // Shared rollover logic
        val roll =
            RolloverUtils.calculateRollover(
                blockTimestamp = block.timestamp,
                delta = totals.delta,
                ctx =
                    RolloverUtils.Context(
                        prevDayTotal = latest?.dayTotal ?: BigInteger.ZERO,
                        prevWeekTotal = latest?.weekTotal ?: BigInteger.ZERO,
                        prevMonthTotal = latest?.monthTotal ?: BigInteger.ZERO,
                        prevYearTotal = latest?.yearTotal ?: BigInteger.ZERO,
                        prevDay = latest?.dayOfMonth,
                        prevWeek = latest?.weekOfYear,
                        prevMonth = latest?.month,
                        prevYear = latest?.year,
                    ),
            )

        // Map into final Mongo document
        return mapToDocument(block, totals, roll)
    }

    /**
     * @notice Loads the latest persisted VTHO record and checks that the incoming block is strictly
     *   newer.
     * @dev Throws if the incoming block number <= last stored block number.
     */
    private fun validateAndLoadLatest(block: Block): VthoGeneratedByBlock? {
        val latest = repository.getLatestRecord()

        if (latest != null && block.number <= latest.blockNumber) {
            throw IllegalStateException(
                "Block ${block.number} is <= last persisted block ${latest.blockNumber}"
            )
        }

        return latest
    }

    /**
     * @param claimed Total VTHO claimed to date (previous + new).
     * @param blockTotal Total VTHO generated at this block including on-chain balance.
     * @param delta Change in blockTotal vs previous block.
     * @notice Aggregated totals derived from events + balanceOf() call.
     */
    data class TotalsForBlock(
        val claimed: BigInteger,
        val blockTotal: BigInteger,
        val delta: BigInteger,
    )

    /**
     * @param events Blockchain events for this block.
     * @param callResponses ABI results for retrieving VTHO balance.
     * @param latest Previously persisted record (null if none).
     * @return Computed totals for the block.
     * @notice Calculate the updated cumulative VTHO totals for the block.
     * @dev
     *     - Reads VTHO balance from call response.
     *     - Adds every event.value to both claimed and total.
     *     - Computes delta = blockTotal - previousTotal.
     */
    private fun calculateTotalsForBlock(
        events: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
        latest: VthoGeneratedByBlock?,
    ): TotalsForBlock {
        val prevClaimed = latest?.rewardsClaimed ?: BigInteger.ZERO
        val prevBlockTotal = latest?.total ?: BigInteger.ZERO

        val vthoBalance = getBalanceOf(callResponses)

        var claimed = prevClaimed
        var blockTotal = vthoBalance + prevClaimed

        events.forEach { evt ->
            val value = evt.requireValue()
            claimed += value
            blockTotal += value
        }

        val delta = blockTotal - prevBlockTotal

        return TotalsForBlock(claimed = claimed, blockTotal = blockTotal, delta = delta)
    }

    /**
     * @return VthoGeneratedByBlock properly filled.
     * @notice Constructs the final MongoDB document for a processed block.
     * @dev Includes:
     *     - Running totals
     *     - Rollover-based period totals
     *     - Block metadata
     *     - Timeframes that rolled (DAY/WEEK/MONTH/YEAR)
     */
    private fun mapToDocument(
        block: Block,
        totals: TotalsForBlock,
        roll: RolloverUtils.RolloverResult,
    ): VthoGeneratedByBlock =
        VthoGeneratedByBlock(
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            total = totals.blockTotal,
            rewardsClaimed = totals.claimed,
            // timestamps
            dayOfMonth = roll.day,
            weekOfYear = roll.week,
            month = roll.month,
            year = roll.year,
            // which periods rolled over
            timeFrames = roll.timeFrames,
            // period totals
            blockTotal = totals.delta,
            dayTotal = roll.dayTotal,
            weekTotal = roll.weekTotal,
            monthTotal = roll.monthTotal,
            yearTotal = roll.yearTotal,
        )

    /** @notice Persist a single per-block VTHO generation record. */
    @Transactional(rollbackFor = [Exception::class])
    open fun save(record: VthoGeneratedByBlock) {
        repository.save(record)
    }

    /**
     * @notice Extract VTHO balance from ABI call responses.
     * @dev Returns zero if ABI data is missing.
     */
    fun getBalanceOf(responses: List<InspectionResult>): BigInteger {
        if (responses.isEmpty() || !responses[0].hasAbiData()) return BigInteger.ZERO

        val decoded =
            FunctionReturnDecoder.decode(
                responses[0].data,
                listOf(InputOutput("uint256", "balance", "uint256")),
            )

        return decoded["balance"] as BigInteger
    }

    /**
     * @notice Require that an event contains a `value` parameter.
     * @dev Throws with helpful context if missing.
     */
    private fun IndexedEvent.requireValue(): BigInteger =
        this.params.getAsBigInteger("value")
            ?: throw IllegalStateException(
                "Event for block $blockNumber (blockId=$blockId) missing 'value'"
            )
}
