package org.vechain.indexer.b3tr.proposal

import java.math.BigInteger
import kotlin.collections.component1
import kotlin.collections.component2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getDescription
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getPower
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getProposalId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getStartRoundId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getWeight
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.groupByProposalId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.groupBySupport
import org.vechain.indexer.b3tr.proposal.ProposalState.Companion.nonFinalizedStates
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ContractUtils

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-results")
@Service
open class ProposalResultService(
    private val repository: ProposalResultRepository,
    private val proposalResultArchiveService: ArchiveService<ProposalResult, ProposalResultArchive>,
    private val proposalResultPruner: TargetedPruner<ProposalResult, ProposalResultArchive>,
    private val thorClient: ThorClient,
    @param:Value("\${business-event.substitutions.B3TR_GOVERNOR_CONTRACT}")
    private val governorContract: String,
) {
    private val statusAbi: AbiElement

    init {
        val response = AbiLoader.load(basePath = "abis/b3tr", names = listOf("state"))
        if (response.size != 1) {
            error("Failed to load ABI for 'state', response size: ${response.size}")
        }

        statusAbi = response.first()
    }

    open fun findByProposalId(proposalId: String): ProposalResult? =
        repository.findByIdOrNull(proposalId)

    open suspend fun updateStatuses(
        block: BlockDetails,
        accumulator: VersionedDocumentAccumulator<ProposalResult>,
    ) {
        val proposals = withContext(Dispatchers.IO) { repository.findByStateIn(nonFinalizedStates) }
        if (proposals.isEmpty()) return

        proposals.chunked(50).forEach { batch ->
            val clauses = createStatusClauses(batch)
            val responses = thorClient.inspectClauses(clauses, BlockRevision.Id(block.blockId))

            batch.forEachIndexed { index, proposal ->
                val response =
                    responses.getOrNull(index)
                        ?: error("Failed to fetch status for proposalId=${proposal.proposalId}")
                val state = parseProposalState(response, proposal.proposalId)

                if (state != null && state != proposal.state) {
                    val (existing, nextVersion) = accumulator.resolve(proposal.proposalId)
                    val updated =
                        proposal.copy(
                            version = nextVersion,
                            blockId = block.blockId,
                            blockNumber = block.blockNumber,
                            blockTimestamp = block.blockTimestamp,
                            state = state,
                        )
                    accumulator.put(proposal.proposalId, existing, updated)
                }
            }
        }
    }

    open fun processBlockEvents(
        events: List<IndexedEvent>,
        accumulator: VersionedDocumentAccumulator<ProposalResult>,
    ) {
        assertEventTypes(events, "B3TR_ProposalCreated", "B3TR_ProposalVote")

        groupByProposalId(events).forEach { (proposalId, proposalEvents) ->
            val createdEvent = proposalEvents.firstOrNull { it.eventType == "B3TR_ProposalCreated" }
            if (createdEvent != null) {
                val blockDetails =
                    BlockDetails(
                        createdEvent.blockId,
                        createdEvent.blockNumber,
                        createdEvent.blockTimestamp,
                    )
                val (existing, nextVersion) = accumulator.resolve(proposalId)
                if (existing != null) {
                    error("Existing ProposalResult found for creation event: $proposalId")
                }
                val created =
                    processCreatedEvent(proposalId, blockDetails, createdEvent, nextVersion)
                accumulator.put(proposalId, existing, created)
            }
            val voteEvents = proposalEvents.filter { it.eventType == "B3TR_ProposalVote" }
            if (voteEvents.isNotEmpty()) {
                val blockDetails =
                    BlockDetails(
                        voteEvents.first().blockId,
                        voteEvents.first().blockNumber,
                        voteEvents.first().blockTimestamp,
                    )
                val (existing, nextVersion) = accumulator.resolve(proposalId)
                val existingResult =
                    existing
                        ?: error("No existing ProposalResult found for vote event: $proposalId")
                val updated =
                    processVoteEvents(
                        proposalId,
                        blockDetails,
                        voteEvents,
                        existingResult,
                        nextVersion,
                    )
                accumulator.put(proposalId, existing, updated)
            }
        }
    }

    /**
     * Creates contract clauses for fetching the current status of proposals.
     *
     * @param proposals The proposals to create clauses for.
     * @return A list of contract clauses.
     */
    protected fun createStatusClauses(proposals: List<ProposalResult>): List<Clause> =
        proposals.map { p ->
            ContractUtils.createClause(governorContract, statusAbi, p.proposalId.toBigInteger())
        }

    /**
     * Parses the proposal state from a contract response.
     *
     * @param response The contract response.
     * @param proposalId The proposal ID (for error messages).
     * @return The parsed proposal state, or null if the response was reverted.
     */
    protected fun parseProposalState(
        response: InspectionResult,
        proposalId: String,
    ): ProposalState? {
        if (response.reverted) {
            error("Failed to fetch status for proposalId=$proposalId (reverted)")
        }

        return ProposalState.fromOrdinal(HexUtils.toInt(response.data))
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
        version: Int,
    ) =
        ProposalResult(
            proposalId = proposalId,
            version = version,
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            createdAtBlockNumber = blockDetails.blockNumber,
            startRoundId = getStartRoundId(event),
            description = getDescription(event),
            state = ProposalState.Pending,
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
        version: Int,
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
            version = version,
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            createdAtBlockNumber = existing.createdAtBlockNumber,
            startRoundId = existing.startRoundId,
            state = existing.state,
            results = updateResults(existing.results, voteEvents),
            description = existing.description,
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
}
