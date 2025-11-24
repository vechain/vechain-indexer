package org.vechain.indexer.vevote

import kotlin.collections.component1
import kotlin.collections.component2
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.proposal.ProposalEventUtils.getProposalId
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.IdUtils.generateId
import org.vechain.indexer.vevote.VeVoteEventUtils.getWeight
import org.vechain.indexer.vevote.VeVoteEventUtils.groupByProposalId
import org.vechain.indexer.vevote.VeVoteEventUtils.groupBySupport

@Profile("vevote", "vevote-results")
@Service
open class VeVoteResultService(
    private val repository: VeVoteProposalResultRepository,
    private val veVoteResultArchiveService:
        ArchiveService<VeVoteProposalResult, VeVoteProposalResultArchive>,
    private val veVoteResultPruner:
        TargetedPruner<VeVoteProposalResult, VeVoteProposalResultArchive>,
    private val mongoTemplate: MongoTemplate,
) {
    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<VeVoteProposalResult>, List<VeVoteProposalResult>> {
        assertEventTypes(events, "VoteCast")

        val updatedResult = mutableMapOf<String, VeVoteProposalResult>()
        val archiveResult = mutableListOf<VeVoteProposalResult>()

        groupByBlock(events).forEach { (blockDetails, blockEvents) ->
            groupByProposalId(blockEvents).forEach { (proposalId, proposalEvents) ->
                groupBySupport(proposalEvents).forEach { (support, supportEvents) ->
                    val recordId = generateId(proposalId, support.name)
                    val existing = resolveExisting(recordId, updatedResult)
                    val updated = createOrUpdateExisting(blockDetails, supportEvents, existing)
                    existing?.let { archiveResult.add(it) }
                    updatedResult[recordId] = updated
                }
            }
        }

        return updatedResult.values.toList() to archiveResult
    }

    open fun save(updated: List<VeVoteProposalResult>, existing: List<VeVoteProposalResult>) {
        saveVersionedDocuments(
            updated,
            existing,
            veVoteResultArchiveService,
            veVoteResultPruner,
            mongoTemplate,
        )
    }

    protected fun createOrUpdateExisting(
        blockDetails: BlockDetails,
        events: List<IndexedEvent>,
        existing: VeVoteProposalResult?,
    ): VeVoteProposalResult {
        require(events.isNotEmpty()) { "No events provided" }

        // All events must have the same proposalId and block details
        val proposalId = getProposalId(events.first())

        require(
            events.all {
                getProposalId(it) == proposalId &&
                    it.blockNumber == blockDetails.blockNumber &&
                    it.blockId == blockDetails.blockId
            }
        ) {
            "All events must have the same proposalId and block"
        }

        // All events must have the same support, and the same as the existing record if present
        val support = VeVoteEventUtils.getSupport(events.first())
        require(events.all { VeVoteEventUtils.getSupport(it) == support }) {
            "All events must have the same support"
        }
        if (existing != null) {
            require(existing.support == support) {
                "Existing record's support does not match the events' support"
            }
        }

        val weight = events.sumOf { getWeight(it) }

        return if (existing != null) {
            require(existing.proposalId == proposalId) {
                "Existing record's proposalId does not match the events' proposalId"
            }

            VeVoteProposalResult(
                id = existing.id,
                version = existing.version + 1,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                proposalId = existing.proposalId,
                support = existing.support,
                totalWeight = existing.totalWeight + weight,
                totalVoters = existing.totalVoters + events.size,
            )
        } else {
            VeVoteProposalResult(
                version = 1,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                proposalId = proposalId,
                support = support,
                totalWeight = weight,
                totalVoters = events.size,
            )
        }
    }

    protected fun resolveExisting(
        recordId: String,
        cache: Map<String, VeVoteProposalResult>,
    ): VeVoteProposalResult? = cache[recordId] ?: repository.findByIdOrNull(recordId)
}
