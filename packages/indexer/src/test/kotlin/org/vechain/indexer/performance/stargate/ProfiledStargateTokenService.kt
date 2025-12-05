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
import org.vechain.indexer.validator.ValidatorSnapshot

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

    override fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
        events: List<IndexedEvent>,
    ): Pair<Collection<StargateToken>, List<StargateToken>> {
        return profiler.time("      StargateTokenService.processBlock") {
            // Access the injected services
            val validatorDelegationServiceField =
                StargateTokenService::class.java.getDeclaredField("validatorDelegationService")
            validatorDelegationServiceField.isAccessible = true
            val validatorDelegationService =
                validatorDelegationServiceField.get(this) as ValidatorDelegationService

            val eventServiceField =
                StargateTokenService::class.java.getDeclaredField("eventService")
            eventServiceField.isAccessible = true
            val eventService = eventServiceField.get(this) as StargateEventService

            // Profile validator snapshot decoding
            val validatorSnapshots =
                profiler.time("        - decodeValidatorSnapshots") {
                    validatorDelegationService.decodeValidatorSnapshots(callResponses)
                }

            val removedValidators =
                profiler.time("        - checkMissingValidators") {
                    checkMissingValidatorsInternal(validatorSnapshots)
                }

            val exitingValidators =
                profiler.time("        - findDelegationsFromExits") {
                    findDelegationsFromExitsInternal(events)
                }

            // DB lookups
            val latestTokenSnapshots =
                profiler.time("        - loadRelevantTokenSnapshots") {
                    loadRelevantTokenSnapshotsInternal(
                        block,
                        events,
                        removedValidators,
                        exitingValidators,
                        validatorSnapshots,
                    )
                }

            val tokensToArchive = mutableListOf<StargateToken>()

            // Mutations
            profiler.time("        - processDelegationStatusTransitions") {
                processDelegationStatusTransitions(block, latestTokenSnapshots, tokensToArchive)
            }

            profiler.time("        - handleValidatorsDisappearedSnapshots") {
                handleValidatorsDisappearedSnapshots(
                    removedValidators,
                    block,
                    latestTokenSnapshots,
                    tokensToArchive,
                )
            }

            profiler.time("        - handleStargateEvents") {
                eventService.handleStargateEvents(
                    events,
                    latestTokenSnapshots,
                    validatorSnapshots,
                    tokensToArchive,
                )
            }

            latestTokenSnapshots.values to tokensToArchive
        }
    }

    override fun save(tokens: Collection<StargateToken>, archive: List<StargateToken>) {
        profiler.time("      StargateTokenService.save (MongoDB)") { super.save(tokens, archive) }
    }

    // Private method accessors using reflection
    private fun loadRelevantTokenSnapshotsInternal(
        block: Block,
        events: List<IndexedEvent>,
        removedValidators: Set<String>,
        exitingValidators: List<String>,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ): MutableMap<String, StargateToken> {
        val method =
            StargateTokenService::class
                .java
                .getDeclaredMethod(
                    "loadRelevantTokenSnapshots",
                    Block::class.java,
                    List::class.java,
                    Set::class.java,
                    List::class.java,
                    Map::class.java,
                )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(
            this,
            block,
            events,
            removedValidators,
            exitingValidators,
            validatorSnapshots,
        ) as MutableMap<String, StargateToken>
    }

    private fun checkMissingValidatorsInternal(
        validatorSnapshots: Map<String, ValidatorSnapshot>
    ): Set<String> {
        val method =
            StargateTokenService::class
                .java
                .getDeclaredMethod("checkMissingValidators", Map::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, validatorSnapshots) as Set<String>
    }

    private fun findDelegationsFromExitsInternal(events: List<IndexedEvent>): List<String> {
        val method =
            StargateTokenService::class
                .java
                .getDeclaredMethod("findDelegationsFromExits", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, events) as List<String>
    }
}
