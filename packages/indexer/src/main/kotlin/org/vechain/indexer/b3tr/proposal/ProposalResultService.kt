package org.vechain.indexer.b3tr.proposal

import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.IdUtils.generateId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getPower
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getProposalId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getSupport
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getWeight
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.groupByProposalId
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.groupBySupport
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
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

        val updatedResult = mutableMapOf<String, ProposalResult>()
        val archiveResult = mutableListOf<ProposalResult>()

        groupByBlock(events).forEach { (_, blockEvents) ->
            groupByProposalId(blockEvents).forEach { (proposalId, proposalEvents) ->
                groupBySupport(proposalEvents).forEach { (support, supportEvents) ->
                    val recordId = generateId(proposalId, support.name)
                    val existing = resolveExisting(recordId, updatedResult)
                    val updated = createOrUpdateExisting(supportEvents, existing)
                    existing?.let { archiveResult.add(it) }
                    updatedResult[recordId] = updated
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
     * Processes a list of events and returns a ProposalResult. If an existing ProposalResult is
     * provided, it updates it; otherwise, it creates a new one.
     *
     * An error is thrown if the events are empty or if they do not have consistent proposalId and
     * block number.
     *
     * @param existing The existing ProposalResult to update, or null to create a new one.
     * @param events The list of IndexedEvents to process.
     * @return A ProposalResult containing the aggregated data from the events.
     */
    protected fun createOrUpdateExisting(
        events: List<IndexedEvent>,
        existing: ProposalResult?,
    ): ProposalResult {
        require(events.isNotEmpty()) { "No events provided" }

        // All events must have the same proposalId and block number
        val blockNumber = events.first().blockNumber
        val proposalId = getProposalId(events.first())

        require(events.all { getProposalId(it) == proposalId && it.blockNumber == blockNumber }) {
            "All events must have the same proposalId and block number"
        }

        return if (existing != null) {
            require(existing.proposalId == proposalId) {
                "Existing record's proposalId does not match the events' proposalId"
            }

            ProposalResult(
                version = existing.version + 1,
                blockId = events.first().blockId,
                blockNumber = blockNumber,
                blockTimestamp = events.first().blockTimestamp,
                proposalId = proposalId,
                support = existing.support, // Assuming support does not change
                voters = existing.voters + events.size.toLong(),
                totalWeight = existing.totalWeight + events.sumOf { getWeight(it) },
                totalPower = existing.totalPower + events.sumOf { getPower(it) },
            )
        } else {
            ProposalResult(
                version = 1,
                blockId = events.first().blockId,
                blockNumber = blockNumber,
                blockTimestamp = events.first().blockTimestamp,
                proposalId = proposalId,
                support =
                    getSupport(events.first()), // Assuming support is taken from the first event
                voters = events.size.toLong(),
                totalWeight = events.sumOf { getWeight(it) },
                totalPower = events.sumOf { getPower(it) },
            )
        }
    }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, ProposalResult>,
    ): ProposalResult? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
