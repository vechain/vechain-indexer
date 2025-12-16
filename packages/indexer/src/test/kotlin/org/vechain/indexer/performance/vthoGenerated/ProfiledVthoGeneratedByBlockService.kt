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
import org.vechain.indexer.validator.domain.ValidatorDecoder.hasAbiData

/**
 * Extended VthoGeneratedByBlockService that profiles EVERY internal method call Tracks performance
 * of:
 * - processBlock (main processing)
 * - save (MongoDB writes)
 * - validateAndLoadLatest (DB reads and validation)
 * - vthoIssued (issuance decoding)
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
            if (events.isEmpty() && !callResponses[0].hasAbiData()) {
                return@time emptyList()
            }

            // Load previous entry & validate ordering
            val latest =
                profiler.time("        - validateAndLoadLatest") {
                    validateAndLoadLatestInternal(block)
                }

            // Compute this block's totals
            val blockTotal = profiler.time("        - vthoIssued") { vthoIssued(callResponses) }

            if (blockTotal == BigInteger.ZERO) {
                return@time emptyList()
            }

            // Shared rollover logic
            val roll =
                profiler.time("        - calculateRollover") {
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
                }

            // Map into final Mongo document
            profiler.time("        - mapToDocument") {
                mapToDocumentInternal(block, blockTotal, roll, latest)
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

    private fun mapToDocumentInternal(
        block: Block,
        blockTotal: BigInteger,
        roll: RolloverUtils.RolloverResult,
        previous: VthoGeneratedByBlock?,
    ): List<VthoGeneratedByBlock> {
        val method =
            VthoGeneratedByBlockService::class
                .java
                .getDeclaredMethod(
                    "mapToDocument",
                    Block::class.java,
                    BigInteger::class.java,
                    RolloverUtils.RolloverResult::class.java,
                    VthoGeneratedByBlock::class.java,
                )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, block, blockTotal, roll, previous) as List<VthoGeneratedByBlock>
    }
}
