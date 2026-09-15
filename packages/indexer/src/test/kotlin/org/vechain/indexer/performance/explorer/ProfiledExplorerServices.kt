package org.vechain.indexer.performance.explorer

import org.vechain.indexer.explorer.AverageFeesPerUserBlockUpdate
import org.vechain.indexer.explorer.AverageFeesPerUserService
import org.vechain.indexer.explorer.BlockUsage
import org.vechain.indexer.explorer.BlockUsageService
import org.vechain.indexer.explorer.ExplorerWriteRepository
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.model.Block

/** [BlockUsageService] with the cumulative counters timed apart from the fee rollup. */
class ProfiledBlockUsageService(
    repository: ExplorerWriteRepository,
    private val profiler: DetailedProfiler,
) : BlockUsageService(repository) {

    override fun processBlock(block: Block): BlockUsage =
        profiler.time("      BlockUsageService.processBlock") { super.processBlock(block) }
}

/** [AverageFeesPerUserService] with its per-block origin and summary reads timed. */
class ProfiledAverageFeesPerUserService(
    repository: ExplorerWriteRepository,
    private val profiler: DetailedProfiler,
) : AverageFeesPerUserService(repository) {

    override fun processBlock(block: Block): AverageFeesPerUserBlockUpdate? =
        profiler.time("      AverageFeesPerUserService.processBlock") { super.processBlock(block) }
}
