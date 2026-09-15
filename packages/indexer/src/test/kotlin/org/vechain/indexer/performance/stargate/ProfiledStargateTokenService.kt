package org.vechain.indexer.performance.stargate

import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.token.StargateEventService
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.StargateTokenService
import org.vechain.indexer.stargate.token.StargateTokenWriteRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.validator.ValidatorReadRepository

/**
 * Extended StargateTokenService that profiles EVERY internal method call Tracks performance of:
 * - processBlock (main processing)
 * - save (database writes)
 * - validator snapshot cache loading
 * - checkMissingValidators (missing validator detection)
 * - validator lifecycle event processing
 * - loadRelevantTokenSnapshots (DB lookups for relevant tokens)
 * - resolveUnknownDelegations (unknown delegation resolution)
 * - processDelegationStatusTransitions (status transitions)
 * - handleValidatorsDisappearedSnapshots (validator disappearance handling)
 * - handleStargateEvents (event processing)
 */
class ProfiledStargateTokenService(
    repository: StargateTokenWriteRepository,
    eventService: StargateEventService,
    validatorRepository: ValidatorReadRepository,
    validatorStartBlock: Long,
    private val profiler: DetailedProfiler,
) :
    StargateTokenService(
        repository,
        eventService,
        validatorRepository,
        validatorStartBlock,
    ) {

    override suspend fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
    ): List<StargateToken> {
        return profiler.timeSuspend("      StargateTokenService.processBlock") {
            super.processBlock(block, events)
        }
    }

    override fun save(tokens: List<StargateToken>) {
        profiler.time("      StargateTokenService.save (Postgres)") { super.save(tokens) }
    }
}
