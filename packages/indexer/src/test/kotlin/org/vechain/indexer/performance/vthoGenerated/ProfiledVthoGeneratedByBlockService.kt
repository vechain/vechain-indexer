package org.vechain.indexer.performance.vthoGenerated

import java.math.BigInteger
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlock
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockRepository
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.RolloverUtils

/**
 * Extended VthoGeneratedByBlockService that profiles EVERY internal method call Tracks performance
 * of:
 * - processBlock (main processing)
 * - save (MongoDB writes)
 * - validateAndLoadLatest (DB reads and validation)
 * - calculateTotalsForBlock (total calculations)
 * - getBalanceOf (balance decoding)
 * - calculateRollover (rollover calculations)
 * - mapToDocument (document mapping)
 */
class ProfiledVthoGeneratedByBlockService(
    repository: VthoGeneratedByBlockRepository,
    private val profiler: DetailedProfiler,
) : VthoGeneratedByBlockService(repository) {

    override fun processBlock(
        events: List<IndexedEvent>,
        block: Block,
        callResponses: List<InspectionResult>,
    ): List<VthoGeneratedByBlock> {
        return profiler.time("      VthoGeneratedByBlockService.processBlock") {
            // Skip blocks with nothing to index
            if (events.isEmpty() && !callResponses[0].data.hasAbiData()) {
                return@time emptyList()
            }

            // Load previous entry & validate ordering
            val latest =
                profiler.time("        - validateAndLoadLatest") {
                    validateAndLoadLatestInternal(block)
                }

            // Compute this block's totals (claimed + total + delta)
            val totals =
                profiler.time("        - calculateTotalsForBlock") {
                    calculateTotalsForBlockInternal(events, callResponses, latest)
                }

            if (totals.blockTotal == BigInteger.ZERO) {
                return@time emptyList()
            }

            // Shared rollover logic
            val roll =
                profiler.time("        - calculateRollover") {
                    RolloverUtils.calculateRollover(
                        blockTimestamp = block.timestamp,
                        delta = totals.delta,
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
                }

            // Map into final Mongo document
            profiler.time("        - mapToDocument") {
                mapToDocumentInternal(block, totals, roll, latest)
            }
        }
    }

    override fun save(records: List<VthoGeneratedByBlock>) {
        profiler.time("      VthoGeneratedByBlockService.save (MongoDB)") { super.save(records) }
    }

    // Private method accessors using reflection
    private fun validateAndLoadLatestInternal(block: Block): VthoGeneratedByBlock? {
        val method =
            VthoGeneratedByBlockService::class
                .java
                .getDeclaredMethod("validateAndLoadLatest", Block::class.java)
        method.isAccessible = true
        return method.invoke(this, block) as? VthoGeneratedByBlock
    }

    private fun calculateTotalsForBlockInternal(
        events: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
        latest: VthoGeneratedByBlock?,
    ): TotalsForBlock {
        // Get the inner class from the service
        val innerClass =
            VthoGeneratedByBlockService::class.java.declaredClasses.first {
                it.simpleName == "TotalsForBlock"
            }

        val method =
            VthoGeneratedByBlockService::class
                .java
                .getDeclaredMethod(
                    "calculateTotalsForBlock",
                    List::class.java,
                    List::class.java,
                    VthoGeneratedByBlock::class.java,
                )
        method.isAccessible = true
        val result = method.invoke(this, events, callResponses, latest)

        // Extract fields from the result
        val claimedField = innerClass.getDeclaredField("claimed")
        claimedField.isAccessible = true
        val blockTotalField = innerClass.getDeclaredField("blockTotal")
        blockTotalField.isAccessible = true
        val deltaField = innerClass.getDeclaredField("delta")
        deltaField.isAccessible = true

        return TotalsForBlock(
            claimed = claimedField.get(result) as BigInteger,
            blockTotal = blockTotalField.get(result) as BigInteger,
            delta = deltaField.get(result) as BigInteger,
        )
    }

    private fun mapToDocumentInternal(
        block: Block,
        totals: TotalsForBlock,
        roll: RolloverUtils.RolloverResult,
        previous: VthoGeneratedByBlock?,
    ): List<VthoGeneratedByBlock> {
        // Get the inner class from the service
        val innerClass =
            VthoGeneratedByBlockService::class.java.declaredClasses.first {
                it.simpleName == "TotalsForBlock"
            }

        // Create an instance of TotalsForBlock using reflection
        val constructor =
            innerClass.getDeclaredConstructor(
                BigInteger::class.java,
                BigInteger::class.java,
                BigInteger::class.java,
            )
        constructor.isAccessible = true
        val totalsInstance =
            constructor.newInstance(totals.claimed, totals.blockTotal, totals.delta)

        val method =
            VthoGeneratedByBlockService::class
                .java
                .getDeclaredMethod(
                    "mapToDocument",
                    Block::class.java,
                    innerClass,
                    RolloverUtils.RolloverResult::class.java,
                    VthoGeneratedByBlock::class.java,
                )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, block, totalsInstance, roll, previous)
            as List<VthoGeneratedByBlock>
    }

    // Helper data class to match the service's inner class
    data class TotalsForBlock(
        val claimed: BigInteger,
        val blockTotal: BigInteger,
        val delta: BigInteger,
    )

    // Extension function to check ABI data
    private fun String.hasAbiData(): Boolean {
        return this.isNotBlank() && this != "0x"
    }
}
