package org.vechain.indexer.performance.stargate

import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.token.StargateEventService
import org.vechain.indexer.stargate.token.StargateToken
import org.vechain.indexer.stargate.token.StargateTokenArchive
import org.vechain.indexer.stargate.token.StargateTokenRepository
import org.vechain.indexer.stargate.token.StargateTokenService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.ValidatorDelegationService

/**
 * Extended StargateTokenService that profiles EVERY internal method call Tracks performance of:
 * - processBlock (main processing)
 * - save (MongoDB writes)
 * - decodeValidatorSnapshots (validator state decoding)
 * - checkMissingValidators (missing validator detection)
 * - findDelegationsFromExits (exit event processing)
 * - loadRelevantTokenSnapshots (DB lookups for relevant tokens)
 * - resolveUnknownDelegations (unknown delegation resolution)
 * - processDelegationStatusTransitions (status transitions)
 * - handleValidatorsDisappearedSnapshots (validator disappearance handling)
 * - handleStargateEvents (event processing)
 */
class ProfiledStargateTokenService(
    repository: StargateTokenRepository,
    eventService: StargateEventService,
    validatorDelegationService: ValidatorDelegationService,
    archiveService: ArchiveService<StargateToken, StargateTokenArchive>,
    private val profiler: DetailedProfiler,
) : StargateTokenService(repository, eventService, validatorDelegationService, archiveService) {

    override suspend fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
        events: List<IndexedEvent>,
    ): Pair<Collection<StargateToken>, List<StargateToken>> {
        return profiler.timeSuspend("      StargateTokenService.processBlock") {
            super.processBlock(block, callResponses, events)
        }
    }

    override fun save(tokens: Collection<StargateToken>, archive: List<StargateToken>) {
        profiler.time("      StargateTokenService.save (MongoDB)") { super.save(tokens, archive) }
    }
}
