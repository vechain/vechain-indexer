package org.vechain.indexer.performance.blockUsage

import org.vechain.indexer.explorer.BlockUsage
import org.vechain.indexer.explorer.BlockUsageService
import org.vechain.indexer.explorer.repository.BlockUsageRepository
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.model.Block

/**
 * Extended BlockUsageService that profiles EVERY internal method call Tracks performance of:
 * - processBlock (main processing)
 * - save (MongoDB writes)
 * - getPreviousBlockUsage (get previous record)
 * - validatePreviousBlockUsage (validation)
 * - createGenesisBlockUsage (for genesis block)
 * - createBlockUsage (for regular blocks)
 * - calculateCumulativeGasLimit
 * - calculateCumulativeGasUsed
 * - calculateCumulativeBaseFeePerGas
 * - calculateCumulativeTransactions
 * - calculateCumulativeClauses
 */
class ProfiledBlockUsageService(
    repository: BlockUsageRepository,
    private val profiler: DetailedProfiler,
) : BlockUsageService(repository) {

    override fun processBlock(block: Block): BlockUsage {
        return profiler.time("      BlockUsageService.processBlock") {
            val previousBlockUsage =
                profiler.time("        - getPreviousBlockUsage") {
                    getPreviousBlockUsage(block.number)
                }

            profiler.time("        - validatePreviousBlockUsage") {
                validatePreviousBlockUsage(previousBlockUsage, block.number)
            }

            val blockUsage =
                if (previousBlockUsage == null) {
                    profiler.time("        - createGenesisBlockUsage") {
                        createGenesisBlockUsage(block)
                    }
                } else {
                    profiler.time("        - createBlockUsage") {
                        createBlockUsageInternal(block, previousBlockUsage)
                    }
                }

            // Update cache via reflection
            val lastProcessedField =
                BlockUsageService::class.java.getDeclaredField("lastProcessedBlockUsage")
            lastProcessedField.isAccessible = true
            lastProcessedField.set(this, blockUsage)

            blockUsage
        }
    }

    override fun save(blockUsage: BlockUsage) {
        profiler.time("      BlockUsageService.save (MongoDB)") { super.save(blockUsage) }
    }

    private fun createBlockUsageInternal(block: Block, previousBlockUsage: BlockUsage): BlockUsage {
        val cumulativeGasLimit =
            profiler.time("          - calculateCumulativeGasLimit") {
                calculateCumulativeGasLimit(previousBlockUsage, block)
            }

        val cumulativeGasUsed =
            profiler.time("          - calculateCumulativeGasUsed") {
                calculateCumulativeGasUsed(previousBlockUsage, block)
            }

        val cumulativeBaseFeePerGas =
            profiler.time("          - calculateCumulativeBaseFeePerGas") {
                calculateCumulativeBaseFeePerGas(previousBlockUsage, block)
            }

        val cumulativeNumTransactions =
            profiler.time("          - calculateCumulativeTransactions") {
                calculateCumulativeTransactions(previousBlockUsage, block)
            }

        val cumulativeNumClauses =
            profiler.time("          - calculateCumulativeClauses") {
                calculateCumulativeClauses(previousBlockUsage, block)
            }

        val isHourly =
            profiler.time("          - calculateTimeBoundary (hourly)") {
                org.vechain.indexer.explorer.TimestampUtils.calculateTimeBoundary(
                    previousBlockUsage.blockTimestamp,
                    block.timestamp,
                    org.vechain.indexer.explorer.TimestampUtils::isHourly,
                )
            }

        val isDaily =
            profiler.time("          - calculateTimeBoundary (daily)") {
                org.vechain.indexer.explorer.TimestampUtils.calculateTimeBoundary(
                    previousBlockUsage.blockTimestamp,
                    block.timestamp,
                    org.vechain.indexer.explorer.TimestampUtils::isDaily,
                )
            }

        val isWeekly =
            profiler.time("          - calculateTimeBoundary (weekly)") {
                org.vechain.indexer.explorer.TimestampUtils.calculateTimeBoundary(
                    previousBlockUsage.blockTimestamp,
                    block.timestamp,
                    org.vechain.indexer.explorer.TimestampUtils::isWeekly,
                )
            }

        val isMonthly =
            profiler.time("          - calculateTimeBoundary (monthly)") {
                org.vechain.indexer.explorer.TimestampUtils.calculateTimeBoundary(
                    previousBlockUsage.blockTimestamp,
                    block.timestamp,
                    org.vechain.indexer.explorer.TimestampUtils::isMonthly,
                )
            }

        return BlockUsage(
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            cumulativeGasLimit = cumulativeGasLimit,
            cumulativeGasUsed = cumulativeGasUsed,
            cumulativeBaseFeePerGas = cumulativeBaseFeePerGas,
            cumulativeNumTransactions = cumulativeNumTransactions,
            cumulativeNumClauses = cumulativeNumClauses,
            isHourly = isHourly,
            isDaily = isDaily,
            isWeekly = isWeekly,
            isMonthly = isMonthly,
        )
    }
}
