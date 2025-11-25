package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.RolloverUtils

/**
 * @title VthoClaimedByBlockService
 * @notice Indexes all **VTHO claimed events** and produces cumulative per-block totals.
 * @dev Responsibilities:
 *     - Enforces strict ascending block order.
 *     - Aggregates claimed VTHO per block.
 *     - Separates **legacy claim events** from modern events.
 *     - Applies global rollover logic (day/week/month/year) via `RolloverUtils`.
 *     - Emits one `VthoClaimedByBlock` record per processed block.
 *
 *   This indexer covers **VTHO claimed**, not generated.
 */
@Profile("stargate", "vtho-claimed-by-block")
@Service
open class VthoClaimedByBlockService(
    private val repository: VthoClaimedByBlockRepository,
    private val archiveService: ArchiveService<VthoClaimedByBlock, VthoClaimedByBlockArchive>,
    private val pruner: TargetedPruner<VthoClaimedByBlock, VthoClaimedByBlockArchive>,
    private val mongoTemplate: MongoTemplate,
) {
    /** @dev Event names representing legacy claim logic. */
    private val legacyEventNames =
        setOf("STARGATE_CLAIM_REWARDS_BASE_LEGACY", "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY")

    data class ProcessResult(
        val records: List<VthoClaimedByBlock>,
        val existing: List<VthoClaimedByBlock>,
    )

    /**
     * @param events The raw decoded events for multiple blocks.
     * @return ProcessResult containing records to persist and existing records to archive.
     * @notice Process all claim events and generate a per-block cumulative dataset.
     * @dev Steps per block:
     *     1. Compute delta claimed (legacy + non-legacy).
     *     2. Update running totals.
     *     3. Apply day/week/month/year rollover logic.
     *     4. Emit finalized record.
     *
     *    The function:
     *     - Returns an ordered list (ascending blockNumber).
     *     - Fails if events include a block number ≤ latest persisted block.
     *     - Uses the first event in each block as the authoritative timestamp.
     */
    open fun processEvents(events: List<IndexedEvent>): ProcessResult {
        if (events.isEmpty()) return ProcessResult(emptyList(), emptyList())

        val latest = repository.getLatestRecord()
        val lastBlock = latest?.blockNumber

        // Enforce correct ordering: monotonic block numbers only
        if (lastBlock != null && events.any { it.blockNumber <= lastBlock }) {
            throw IllegalStateException("Events include block ≤ last persisted block $lastBlock")
        }

        // Group per block and sort ascending
        val grouped = events.groupBy { it.blockNumber }.toSortedMap()

        var runningTotal = latest?.total ?: BigInteger.ZERO
        var runningLegacy = latest?.legacyRewards ?: BigInteger.ZERO

        val output = mutableListOf<VthoClaimedByBlock>()
        val existing = mutableListOf<VthoClaimedByBlock>()
        var prev: VthoClaimedByBlock? = latest

        for ((blockNum, blockEvents) in grouped) {
            var deltaLatest = BigInteger.ZERO
            var deltaLegacy = BigInteger.ZERO

            for (evt in blockEvents) {
                val v = evt.requireValue()
                if (evt.eventType in legacyEventNames) {
                    deltaLegacy += v
                } else {
                    deltaLatest += v
                }
            }

            runningTotal += deltaLatest
            runningLegacy += deltaLegacy

            val rep = blockEvents.first()

            val roll =
                RolloverUtils.calculateRollover(
                    blockTimestamp = rep.blockTimestamp,
                    delta = deltaLatest,
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
                existing += prev
                output += prev.copy(version = prev.version + 1, timeFrames = roll.timeFrames)
            }

            val record =
                VthoClaimedByBlock(
                    version = 1,
                    blockId = rep.blockId,
                    blockNumber = blockNum,
                    blockTimestamp = rep.blockTimestamp,
                    total = runningTotal,
                    legacyRewards = runningLegacy,
                    // Rollover timestamps
                    hourOfDay = roll.hour,
                    dayOfMonth = roll.day,
                    weekOfYear = roll.week,
                    month = roll.month,
                    year = roll.year,
                    timeFrames = emptyList(),
                    // Period totals
                    blockTotal = deltaLatest,
                    hourTotal = roll.hourTotal,
                    dayTotal = roll.dayTotal,
                    weekTotal = roll.weekTotal,
                    monthTotal = roll.monthTotal,
                    yearTotal = roll.yearTotal,
                )

            output += record
            prev = record
        }

        return ProcessResult(output, existing)
    }

    /**
     * @param records The list of `VthoClaimedByBlock` rows to store.
     * @param existing The list of existing documents that are being updated (for archiving).
     * @notice Persist a batch of per-block claimed-VTHO records.
     */
    open fun saveRecords(records: List<VthoClaimedByBlock>, existing: List<VthoClaimedByBlock>) {
        saveVersionedDocuments(records, existing, archiveService, pruner, mongoTemplate)
    }
}

/**
 * @notice Require the presence of parameter `"value"` within an event.
 * @dev Throws an exception with full block context if the parameter is absent.
 */
private fun IndexedEvent.requireValue(): BigInteger =
    this.params.getAsBigInteger("value")
        ?: throw IllegalStateException(
            "Event for block $blockNumber (blockId=$blockId) is missing required 'value'"
        )
