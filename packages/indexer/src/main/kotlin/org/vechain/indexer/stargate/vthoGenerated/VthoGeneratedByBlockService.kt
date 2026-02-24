package org.vechain.indexer.stargate.vthoGenerated

import java.math.BigInteger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.RolloverUtils
import org.vechain.indexer.validator.domain.ValidatorDecoder.hasAbiData

@Profile("stargate", "vtho-generated-by-block")
@Service
open class VthoGeneratedByBlockService(private val repository: VthoGeneratedByBlockRepository) {
    private val logger = LoggerFactory.getLogger(VthoGeneratedByBlockService::class.java)
    private var latestRecordCache: VthoGeneratedByBlock? = null

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
        block: Block,
        callResponses: List<InspectionResult>,
    ): List<VthoGeneratedByBlock> {
        // Skip blocks with nothing to index
        if (!callResponses[0].hasAbiData()) return emptyList()

        // Load previous entry & validate ordering
        val latest = validateAndLoadLatest(block)

        // Compute this block's totals (claimed + total + delta)
        val blockTotal = vthoIssued(callResponses)
        if (blockTotal == BigInteger.ZERO) return emptyList()

        // Shared rollover logic
        val roll =
            RolloverUtils.calculateRollover(
                blockTimestamp = block.timestamp,
                delta = blockTotal,
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

        // Map into final Mongo document
        return mapToDocument(block, blockTotal, roll, latest)
    }

    private fun validateAndLoadLatest(block: Block): VthoGeneratedByBlock? {
        val latest = loadLatest(block) ?: return null

        if (block.number != latest.blockNumber + 1) {
            throw IllegalStateException(
                "Block ${block.number} is not the next block after last persisted block ${latest.blockNumber}"
            )
        }

        return latest
    }

    private fun loadLatest(block: Block): VthoGeneratedByBlock? {
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

        return repository.findLatest()
    }

    /**
     * @param block The current block metadata (id, number, timestamp).
     * @param blockTotal Aggregated running totals and delta for this block.
     * @param roll Result of applying rollover logic (new period totals + timeframes crossed).
     * @param previous The latest persisted record before this block (null if none).
     * @return A list containing:
     *         - An updated previous record (only when rollover happens)
     *         - The new current block record
     *
     * @notice Construct one or two MongoDB documents representing VTHO generation state.
     * @dev This function returns **multiple documents** when a rollover occurs:
     *     - If no day/week/month/year boundary is crossed: → Only the *current block's* document is
     *       returned.
     *     - If a rollover DID occur: → A modified copy of the **previous document** is returned
     *       first. • Its `timeFrames` field is updated to reflect which periods rolled. → Then the
     *       *current block's* record is returned.
     *
     * Returning both documents ensures that rollover information is persisted exactly at the
     * boundary where it occurs.
     */
    private fun mapToDocument(
        block: Block,
        blockTotal: BigInteger,
        roll: RolloverUtils.RolloverResult,
        previous: VthoGeneratedByBlock?,
    ): List<VthoGeneratedByBlock> {
        val result = mutableListOf<VthoGeneratedByBlock>()

        if (roll.timeFrames.isNotEmpty() && previous != null) {
            result += previous.copy(timeFrames = roll.timeFrames)
        }

        val total = (previous?.total ?: BigInteger.ZERO) + blockTotal

        result +=
            VthoGeneratedByBlock(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                total = total,
                // timestamps
                hourOfDay = roll.hour,
                dayOfMonth = roll.day,
                weekOfYear = roll.week,
                month = roll.month,
                year = roll.year,
                timeFrames = emptyList(),
                // period totals
                blockTotal = blockTotal,
                hourTotal = roll.hourTotal,
                dayTotal = roll.dayTotal,
                weekTotal = roll.weekTotal,
                monthTotal = roll.monthTotal,
                yearTotal = roll.yearTotal,
            )

        return result
    }

    /** @notice Persist VTHO generation records. */
    @Transactional(rollbackFor = [Exception::class])
    open fun save(records: List<VthoGeneratedByBlock>) {
        if (records.isEmpty()) return
        repository.saveAll(records)
        latestRecordCache = records.maxBy { it.blockNumber }
    }

    /**
     * @notice Extract VTHO Issued from ABI call responses.
     * @dev Returns zero if ABI data is missing.
     */
    fun vthoIssued(responses: List<InspectionResult>): BigInteger {
        if (responses.isEmpty() || !responses[0].hasAbiData()) {
            return BigInteger.ZERO
        }

        val decoded =
            FunctionReturnDecoder.decode(
                responses[0].data,
                listOf(InputOutput("uint256", "issued", "uint256")),
            )

        return decoded["issued"] as BigInteger
    }
}
