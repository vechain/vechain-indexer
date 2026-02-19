package org.vechain.indexer.performance.delegation

import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.DelegationService
import org.vechain.indexer.validator.ValidatorDelegationService

/**
 * Extended DelegationService that profiles EVERY internal method call Tracks performance of:
 * - processBlock (main processing)
 * - save (MongoDB writes)
 * - findDueDelegations (DB reads for due delegations)
 * - findDelegationsFromEvents (DB reads from events)
 * - findDelegationsFromExits (DB reads from validator exits)
 * - checkForUpdatesOnUnknown (validation period checks)
 * - applyScheduledTransitions (lifecycle transitions)
 * - applyEventMutations (event processing)
 * - checkMissingValidators (validator state checking)
 */
class ProfiledDelegationService(
    repository: DelegationRepository,
    archiveService: ArchiveService<Delegation>,
    validatorDelegationService: ValidatorDelegationService,
    stakerSC: String,
    private val profiler: DetailedProfiler,
) :
    DelegationService(
        repository,
        archiveService,
        delegationPruner,
        validatorDelegationService,
        stakerSC,
    ) {

    override suspend fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
    ): Pair<List<Delegation>, List<Delegation>> {
        return profiler.timeSuspend("      DelegationService.processBlock") {
            super.processBlock(block, events, callResponses)
        }
    }

    override fun save(updates: List<Delegation>, archive: List<Delegation>) {
        profiler.time("      DelegationService.save (MongoDB)") { super.save(updates, archive) }
    }
}
