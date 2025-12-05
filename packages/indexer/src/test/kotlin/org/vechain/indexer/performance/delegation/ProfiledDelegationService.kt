package org.vechain.indexer.performance.delegation

import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationArchive
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
    archiveService: ArchiveService<Delegation, DelegationArchive>,
    validatorDelegationService: ValidatorDelegationService,
    stakerSC: String,
    private val profiler: DetailedProfiler,
) : DelegationService(repository, archiveService, validatorDelegationService, stakerSC) {

    override fun processBlock(
        block: Block,
        events: List<IndexedEvent>,
        callResponses: List<InspectionResult>,
    ): Pair<List<Delegation>, List<Delegation>> {
        return profiler.time("      DelegationService.processBlock") {
            val validatorSnapshots =
                profiler.time("        - decodeValidatorSnapshots") {
                    decodeValidatorSnapshotsInternal(callResponses)
                }

            val disappeared =
                profiler.time("        - checkMissingValidators") {
                    checkMissingValidatorsInternal(validatorSnapshots)
                }

            // 1. Collect delegations from different sources
            val (unknownStart, due) =
                profiler.time("        - findDueDelegations") { findDueDelegationsInternal(block) }

            val eventDelegations =
                profiler.time("        - findDelegationsFromEvents") {
                    findDelegationsFromEventsInternal(events)
                }

            val validatorExitDelegations =
                profiler.time("        - findDelegationsFromExits") {
                    findDelegationsFromExitsInternal(events, disappeared)
                }

            // 2. Build working set (deduped by delegation ID)
            val delegations =
                (unknownStart + due + eventDelegations + validatorExitDelegations)
                    .associateBy { it.id }
                    .toMutableMap()
            val delegationsToArchive = mutableListOf<Delegation>()

            // 3. Check status on delegations with unknown start blocks
            profiler.time("        - checkForUpdatesOnUnknown") {
                checkForUpdatesOnUnknownInternal(
                    unknownStart,
                    block,
                    delegations,
                    delegationsToArchive,
                    validatorSnapshots,
                )
            }

            // 4. Apply lifecycle transitions + event mutations
            profiler.time("        - applyScheduledTransitions") {
                applyScheduledTransitionsInternal(block, delegations, delegationsToArchive)
            }

            profiler.time("        - applyEventMutations") {
                applyEventMutationsInternal(
                    events,
                    delegations,
                    delegationsToArchive,
                    block,
                    validatorSnapshots,
                )
            }

            // 5. Handle validators that disappeared entirely
            profiler.time("        - handleValidatorDisappeared") {
                disappeared.forEach {
                    handleValidatorDisappearedInternal(it, delegations, delegationsToArchive, block)
                }
            }

            delegations.values.toList() to delegationsToArchive
        }
    }

    override fun save(updates: List<Delegation>, archive: List<Delegation>) {
        profiler.time("      DelegationService.save (MongoDB)") { super.save(updates, archive) }
    }

    // Private method accessors using reflection
    private fun decodeValidatorSnapshotsInternal(
        callResponses: List<InspectionResult>
    ): Map<String, org.vechain.indexer.validator.ValidatorSnapshot> {
        // Access validatorDelegationService field
        val field = DelegationService::class.java.getDeclaredField("validatorDelegationService")
        field.isAccessible = true
        val validatorDelegationService = field.get(this) as ValidatorDelegationService
        return validatorDelegationService.decodeValidatorSnapshots(callResponses)
    }

    private fun checkMissingValidatorsInternal(
        validatorsSnapshots: Map<String, org.vechain.indexer.validator.ValidatorSnapshot>
    ): Set<String> {
        val method =
            DelegationService::class
                .java
                .getDeclaredMethod("checkMissingValidators", Map::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, validatorsSnapshots) as Set<String>
    }

    private fun findDueDelegationsInternal(block: Block): Pair<List<Delegation>, List<Delegation>> {
        val method =
            DelegationService::class.java.getDeclaredMethod("findDueDelegations", Block::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, block) as Pair<List<Delegation>, List<Delegation>>
    }

    private fun findDelegationsFromEventsInternal(events: List<IndexedEvent>): List<Delegation> {
        val method =
            DelegationService::class
                .java
                .getDeclaredMethod("findDelegationsFromEvents", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, events) as List<Delegation>
    }

    private fun findDelegationsFromExitsInternal(
        events: List<IndexedEvent>,
        disappeared: Set<String>,
    ): List<Delegation> {
        val method =
            DelegationService::class
                .java
                .getDeclaredMethod("findDelegationsFromExits", List::class.java, Set::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, events, disappeared) as List<Delegation>
    }

    private fun checkForUpdatesOnUnknownInternal(
        unknown: List<Delegation>,
        block: Block,
        delegations: MutableMap<String, Delegation>,
        archive: MutableList<Delegation>,
        validatorsSnapshots: Map<String, org.vechain.indexer.validator.ValidatorSnapshot>,
    ) {
        val method =
            DelegationService::class
                .java
                .getDeclaredMethod(
                    "checkForUpdatesOnUnknown",
                    List::class.java,
                    Block::class.java,
                    MutableMap::class.java,
                    MutableList::class.java,
                    Map::class.java,
                )
        method.isAccessible = true
        method.invoke(this, unknown, block, delegations, archive, validatorsSnapshots)
    }

    private fun applyScheduledTransitionsInternal(
        block: Block,
        delegations: MutableMap<String, Delegation>,
        archive: MutableList<Delegation>,
    ) {
        val method =
            DelegationService::class
                .java
                .getDeclaredMethod(
                    "applyScheduledTransitions",
                    Block::class.java,
                    MutableMap::class.java,
                    MutableList::class.java,
                )
        method.isAccessible = true
        method.invoke(this, block, delegations, archive)
    }

    private fun applyEventMutationsInternal(
        events: List<IndexedEvent>,
        delegations: MutableMap<String, Delegation>,
        delegationsToArchive: MutableList<Delegation>,
        block: Block,
        validatorSnapshots: Map<String, org.vechain.indexer.validator.ValidatorSnapshot>,
    ) {
        val method =
            DelegationService::class
                .java
                .getDeclaredMethod(
                    "applyEventMutations",
                    List::class.java,
                    MutableMap::class.java,
                    MutableList::class.java,
                    Block::class.java,
                    Map::class.java,
                )
        method.isAccessible = true
        method.invoke(this, events, delegations, delegationsToArchive, block, validatorSnapshots)
    }

    private fun handleValidatorDisappearedInternal(
        validatorId: String,
        delegations: MutableMap<String, Delegation>,
        delegationsToArchive: MutableList<Delegation>,
        block: Block,
    ) {
        val method =
            DelegationService::class
                .java
                .getDeclaredMethod(
                    "handleValidatorDisappeared",
                    String::class.java,
                    MutableMap::class.java,
                    MutableList::class.java,
                    Block::class.java,
                )
        method.isAccessible = true
        method.invoke(this, validatorId, delegations, delegationsToArchive, block)
    }
}
