package org.vechain.indexer.performance.vthoGenerated

import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlock
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedByBlockService
import org.vechain.indexer.stargate.vthoGenerated.VthoGeneratedWriteRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult

/** [VthoGeneratedByBlockService] with its processing and its Postgres writes timed separately. */
class ProfiledVthoGeneratedByBlockService(
    repository: VthoGeneratedWriteRepository,
    private val profiler: DetailedProfiler,
) : VthoGeneratedByBlockService(repository) {

    override fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): List<VthoGeneratedByBlock> =
        profiler.time("      VthoGeneratedByBlockService.processBlock") {
            super.processBlock(block, callResponses)
        }

    override fun save(records: List<VthoGeneratedByBlock>) {
        profiler.time("      VthoGeneratedByBlockService.save (Postgres)") { super.save(records) }
    }
}
