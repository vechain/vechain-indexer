package org.vechain.indexer.performance.validatorBlock

import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.ValidatorBlock
import org.vechain.indexer.validator.ValidatorBlockRepository
import org.vechain.indexer.validator.ValidatorBlockService
import org.vechain.indexer.validator.ValidatorRepository

/**
 * Thin profiling wrapper around [ValidatorBlockService] for the performance test harness. Captures
 * end-to-end timings on `processBlock` and `save`. The previous per-phase breakdown depended on the
 * V1 aggregator decode pipeline; with V2 the per-block path is short enough that top-level timings
 * are sufficient.
 */
class ProfiledValidatorBlockService(
    repository: ValidatorBlockRepository,
    validatorRepository: ValidatorRepository,
    thorClient: ThorClient,
    private val profiler: DetailedProfiler,
) : ValidatorBlockService(repository, validatorRepository, thorClient) {

    override suspend fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): List<ValidatorBlock> =
        profiler.time("      ValidatorBlockService.processBlock") {
            super.processBlock(block, callResponses)
        }

    override fun save(records: List<ValidatorBlock>) {
        profiler.time("      ValidatorBlockService.save (MongoDB)") { super.save(records) }
    }
}
