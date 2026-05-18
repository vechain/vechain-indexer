package org.vechain.indexer.performance.stargate

import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.token.StargateEventService
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.StargateTokenRepository
import org.vechain.indexer.stargate.token.StargateTokenService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.validator.ValidatorRepository

/**
 * Extended StargateTokenService that profiles EVERY internal method call Tracks performance of:
 * - processBlock (main processing)
 * - save (MongoDB writes)
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
    repository: StargateTokenRepository,
    eventService: StargateEventService,
    validatorRepository: ValidatorRepository,
    mongoTemplate: MongoTemplate,
    inlineVersioningProperties: InlineVersioningProperties,
    validatorStartBlock: Long,
    private val profiler: DetailedProfiler,
) :
    StargateTokenService(
        repository,
        eventService,
        validatorRepository,
        mongoTemplate,
        inlineVersioningProperties,
        validatorStartBlock,
    ) {

    override suspend fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
    ): Pair<Collection<StargateToken>, List<StargateToken>> {
        return profiler.timeSuspend("      StargateTokenService.processBlock") {
            super.processBlock(block, events)
        }
    }

    override fun save(tokens: Collection<StargateToken>, existing: List<StargateToken>) {
        profiler.time("      StargateTokenService.save (MongoDB)") { super.save(tokens, existing) }
    }
}
