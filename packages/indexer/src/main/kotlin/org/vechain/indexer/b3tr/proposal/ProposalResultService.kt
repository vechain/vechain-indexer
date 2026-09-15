package org.vechain.indexer.b3tr.proposal

import java.math.BigInteger
import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getDescription
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getPower
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getProposalId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getStartRoundId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getWeight
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.groupByProposalId
import org.vechain.indexer.b3tr.proposal.ProposalState.Companion.nonFinalizedStates
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-proposal")
@Service
open class ProposalResultService(
    private val repository: ProposalWriteRepository,
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

    /** The new row of each proposal touched in each block, in ascending block order. */
    open fun processEvents(events: List<IndexedEvent>): List<ProposalResult> {
        val relevant = events.filter {
            it.eventType == "B3TR_ProposalCreated" || it.eventType == "B3TR_ProposalVote"
        }
        if (relevant.isEmpty()) return emptyList()

        val current =
            repository
                .findCurrent(relevant.map(::getProposalId).toSet())
                .associateBy { it.proposalId }
                .toMutableMap()
        // One row per (block, proposal): a later event in the same block replaces the earlier.
        val rows = linkedMapOf<Pair<Long, String>, ProposalResult>()

        groupByBlock(relevant).forEach { (blockDetails, blockEvents) ->
            processBlockEvents(blockEvents, blockDetails, current).forEach { updated ->
                current[updated.proposalId] = updated
                rows[blockDetails.blockNumber to updated.proposalId] = updated
            }
        }
        return rows.values.toList()
    }

    /**
     * The proposals whose on-chain state has moved since it was last read, as of [block]. Only
     * called at the chain head: a state changes with the round, not with an event, so nothing
     * during a fast sync can observe it.
     */
    open suspend fun refreshStates(
        block: BlockDetails,
        pending: List<ProposalResult>,
    ): List<ProposalResult> {
        val fromEvents = pending.associateBy { it.proposalId }
        val proposals =
            (repository.findCurrentByStates(nonFinalizedStates).map {
                    fromEvents[it.proposalId] ?: it
                } + fromEvents.values.filter { it.state in nonFinalizedStates })
                .distinctBy { it.proposalId }
        if (proposals.isEmpty()) return emptyList()

        return proposals.chunked(STATUS_BATCH_SIZE).flatMap { batch ->
            val responses =
                thorClient.inspectClauses(
                    createStatusClauses(batch),
                    BlockRevision.Id(block.blockId),
                )
            batch.mapIndexedNotNull { index, proposal ->
                val response =
                    responses.getOrNull(index)
                        ?: error("Failed to fetch status for proposalId=${proposal.proposalId}")
                val state = parseProposalState(response, proposal.proposalId)
                if (state == null || state == proposal.state) null
                else
                    proposal.copy(
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp,
                        state = state,
                    )
            }
        }
    }

    private fun processBlockEvents(
        events: List<IndexedEvent>,
        blockDetails: BlockDetails,
        current: Map<String, ProposalResult>,
    ): List<ProposalResult> {
        assertEventTypes(events, "B3TR_ProposalCreated", "B3TR_ProposalVote")

        return groupByProposalId(events).map { (proposalId, proposalEvents) ->
            val createdEvent = proposalEvents.firstOrNull { it.eventType == "B3TR_ProposalCreated" }
            val voteEvents = proposalEvents.filter { it.eventType == "B3TR_ProposalVote" }
            val existing = current[proposalId]

            val created =
                if (createdEvent == null) {
                    existing ?: error("No existing ProposalResult found for vote: $proposalId")
                } else {
                    require(existing == null) {
                        "Existing ProposalResult found for creation event: $proposalId"
                    }
                    processCreatedEvent(proposalId, blockDetails, createdEvent)
                }

            if (voteEvents.isEmpty()) created
            else processVoteEvents(blockDetails, voteEvents, created)
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
     * Adds a block's votes to the running totals of [existing].
     *
     * @param blockDetails The details of the block containing the events.
     * @param voteEvents The list of IndexedEvents representing votes.
     * @param existing The ProposalResult the votes are cast against.
     */
    protected fun processVoteEvents(
        blockDetails: BlockDetails,
        voteEvents: List<IndexedEvent>,
        existing: ProposalResult,
    ): ProposalResult =
        existing.copy(
            blockId = blockDetails.blockId,
            blockNumber = blockDetails.blockNumber,
            blockTimestamp = blockDetails.blockTimestamp,
            results = updateResults(existing.results, voteEvents),
        )

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
        val votesBySupport = ProposalEventUtils.groupBySupport(voteEvents)

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

    private companion object {
        const val STATUS_BATCH_SIZE = 50
    }
}
