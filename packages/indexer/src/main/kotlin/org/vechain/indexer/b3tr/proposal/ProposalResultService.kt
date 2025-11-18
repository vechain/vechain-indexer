package org.vechain.indexer.b3tr.proposal

import java.math.BigInteger
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getPower
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getProposalId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getStartRoundId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getWeight
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.groupByProposalId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.groupBySupport
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-results")
@Service
open class ProposalResultService(
    private val repository: ProposalResultRepository,
    private val proposalResultArchiveService: ArchiveService<ProposalResult, ProposalResultArchive>,
    private val proposalResultPruner: TargetedPruner<ProposalResult, ProposalResultArchive>,
) {

    /**
     * Processes a list of events and returns a pair of lists:
     * - The first list contains updated proposal results.
     * - The second list contains archived proposal results.
     *
     * @param events The list of indexed events to process.
     * @return A pair of lists containing updated and archived proposal results.
     */
    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<ProposalResult>, List<ProposalResult>> {
        // Ensure all events are of type B3TR_ProposalCreated or B3TR_ProposalVote
        assertEventTypes(events, "B3TR_ProposalCreated", "B3TR_ProposalVote")

        val updatedResult = mutableMapOf<String, ProposalResult>()
        val archiveResult = mutableListOf<ProposalResult>()

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            groupByProposalId(blockEvents).forEach { (proposalId, proposalEvents) ->
                // Check for ProposalCreated event (at most one)
                val createdEvent =
                    proposalEvents.firstOrNull { it.eventType == "B3TR_ProposalCreated" }
                if (createdEvent != null) {
                    val existing = resolveExisting(proposalId, updatedResult)
                    if (existing != null) {
                        error("Existing ProposalResult found for creation event: $proposalId")
                    }
                    updatedResult[proposalId] =
                        processCreatedEvent(proposalId, blockDetails, createdEvent)
                }
                // Process voting events
                val voteEvents = proposalEvents.filter { it.eventType == "B3TR_ProposalVote" }
                if (voteEvents.isNotEmpty()) {
                    val existing =
                        resolveExisting(proposalId, updatedResult)
                            ?: error("No existing ProposalResult found for vote event: $proposalId")
                    archiveResult.add(existing)
                    updatedResult[proposalId] =
                        processVoteEvents(proposalId, blockDetails, voteEvents, existing)
                }
            }
        }

        return updatedResult.values.toList() to archiveResult
    }

    /**
     * Saves the updated proposal results and archives the existing ones. This method is
     * transactional and will roll back in case of any exception.
     *
     * @param updated The list of updated proposal results to save.
     * @param existing The list of existing proposal results to archive.
     * @throws Exception if any error occurs during the save operation.
     * @see ProposalResultRepository.saveAll
     */
    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<ProposalResult>, existing: List<ProposalResult>) {
        saveVersionedDocuments(
            updated,
            existing,
            repository,
            proposalResultArchiveService,
            proposalResultPruner,
        )
    }

    /**
     * Processes a ProposalCreated event and returns a new ProposalResult.
     *
     * @param proposalId The ID of the proposal.
     * @param blockDetails The details of the block containing the event.
     * @param event The IndexedEvent representing the ProposalCreated event.
     * @return A new ProposalResult initialized from the creation event.
     */
    protected fun processCreatedEvent(
        proposalId: String,
        blockDetails: BlockDetails,
        event: IndexedEvent,
    ) =
        ProposalResult(
            proposalId = proposalId,
            version = 1,
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            createdAtBlockNumber = blockDetails.blockNumber,
            startRoundId = getStartRoundId(event),
            results = null,
        )

    /**
     * Processes a list of events and returns a ProposalResult. If an existing ProposalResult is
     * provided, it updates it; otherwise, it creates a new one.
     *
     * An error is thrown if the events are empty or if they do not have consistent proposalId and
     * block number.
     *
     * @param proposalId The ID of the proposal.
     * @param blockDetails The details of the block containing the events.
     * @param voteEvents The list of IndexedEvents representing votes.
     * @param existing The existing ProposalResult to update, or null to create a new one.
     * @return A ProposalResult containing the aggregated data from the events.
     */
    protected fun processVoteEvents(
        proposalId: String,
        blockDetails: BlockDetails,
        voteEvents: List<IndexedEvent>,
        existing: ProposalResult,
    ): ProposalResult {
        require(voteEvents.isNotEmpty()) { "No events provided" }

        // All events must have the same proposalId and block number
        require(
            voteEvents.all {
                getProposalId(it) == proposalId && it.blockNumber == blockDetails.blockNumber
            }
        ) {
            "All events must have the same proposalId and block number"
        }

        require(existing.proposalId == proposalId) {
            "Existing record's proposalId does not match the events' proposalId"
        }

        return ProposalResult(
            proposalId = proposalId,
            version = existing.version + 1,
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            createdAtBlockNumber = existing.createdAtBlockNumber,
            startRoundId = existing.startRoundId,
            results = updateResults(existing.results, voteEvents),
        )
    }

    /**
     * Updates the VoteResults based on the provided vote events.
     *
     * @param results The existing VoteResults to update, or null to create new results.
     * @param voteEvents The list of IndexedEvents representing votes.
     * @return The updated VoteResults.
     */
    protected fun updateResults(
        results: VoteResults?,
        voteEvents: List<IndexedEvent>,
    ): VoteResults {
        val votesBySupport = groupBySupport(voteEvents)

        // If results is null, initialize with zero values
        val existingResults =
            results
                ?: VoteResults(
                    forResult = Result(0L, BigInteger.ZERO, BigInteger.ZERO),
                    againstResult = Result(0L, BigInteger.ZERO, BigInteger.ZERO),
                    abstainResult = Result(0L, BigInteger.ZERO, BigInteger.ZERO),
                )

        return VoteResults(
            forResult =
                updateResultForSupport(existingResults.forResult, votesBySupport[Support.FOR]),
            againstResult =
                updateResultForSupport(
                    existingResults.againstResult,
                    votesBySupport[Support.AGAINST],
                ),
            abstainResult =
                updateResultForSupport(
                    existingResults.abstainResult,
                    votesBySupport[Support.ABSTAIN],
                ),
        )
    }

    /**
     * Updates a single Result by accumulating vote data from the provided events.
     *
     * @param result The existing Result to update.
     * @param events The list of IndexedEvents for this support type, or null if none.
     * @return The updated Result with accumulated voters, weight, and power.
     */
    protected fun updateResultForSupport(result: Result, events: List<IndexedEvent>?): Result {
        if (events == null) return result

        return result.copy(
            voters = result.voters + events.size.toLong(),
            totalWeight = result.totalWeight + events.sumOf { getWeight(it) },
            totalPower = result.totalPower + events.sumOf { getPower(it) },
        )
    }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, ProposalResult>,
    ): ProposalResult? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
