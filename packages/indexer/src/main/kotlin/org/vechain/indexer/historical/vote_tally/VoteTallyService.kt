package org.vechain.indexer.historical.vote_tally

import kotlin.jvm.java
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.stereotype.Service
import org.vechain.indexer.event.JsonLoader.logger
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.ThorService

@Profile("historical-proposals")
@Service
open class VoteTallyService(
    private val thorService: ThorService,
    private val voteTallyRepository: VoteTallyRepository,
    @Value("\${veworld.contract.historical_proposals.steering_committee}")
    private val steeringCommitteeAddress: String,
    @Value("\${veworld.contract.historical_proposals.all_stakeholders}")
    private val allStakeholdersAddress: String,
    private val mongoTemplate: MongoTemplate,
) {
    private var bestBlockNumber: Long = 0
    private var hasReachedCurrentBlock = false

    fun processNewVotes(events: List<IndexedEvent>, currentBlockNumber: Long?): List<VoteTally> {
        if (!hasReachedCurrentBlock) {
            val bestBlock = thorService.getBestBlock()
            bestBlockNumber = bestBlock.number
            val blockCheck = currentBlockNumber ?: events.firstOrNull()?.blockNumber

            if (blockCheck != null && blockCheck >= bestBlockNumber) {
                logger.info("Vote tally indexer has reached the current $bestBlockNumber")
                aggregateAndUpdateProposals()
                hasReachedCurrentBlock = true
                return emptyList()
            }
        }
        if (hasReachedCurrentBlock) {
            return emptyList()
        }
        return events.mapNotNull { event ->
            try {
                when (event.address) {
                    this.steeringCommitteeAddress -> processSteeringCommitteeVote(event)
                    this.allStakeholdersAddress -> processAllStakeholdersVote(event)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun aggregateAndUpdateProposals() {
        logger.info("Starting vote tally aggregation...")
        val startTime = System.currentTimeMillis()

        //  count votes per option
        val groupByProposalAndOption =
            Aggregation.group("proposalId", "selectedOptions").count().`as`("count")

        // renaming column
        val projectResult =
            Aggregation.project()
                .andExpression("_id.proposalId")
                .`as`("proposalId")
                .andExpression("_id.selectedOptions")
                .`as`("option")
                .andExpression("count")
                .`as`("count")

        val aggregation = Aggregation.newAggregation(groupByProposalAndOption, projectResult)

        val results =
            mongoTemplate
                .aggregate(aggregation, "historical-vote-tally", ResultClass::class.java)
                .mappedResults

        val duration = System.currentTimeMillis() - startTime
        logger.info("Vote tallying complete. Processed ${results.size} results in ${duration}ms")
        results.forEach { result ->
            logger.info(
                "Proposal ${result.proposalId}: Option ${result.selectedOptions} has ${result.count} votes"
            )
        }
    }

    private fun processSteeringCommitteeVote(event: IndexedEvent): VoteTally? {
        val param = event.params.getReturnValues()
        val proposalId = param["proposalId"]?.toString() ?: return null
        val voter = param["voter"]?.toString() ?: return null
        val optionRaw = param["options"]?.toString() ?: return null

        return VoteTally(
            id = "${proposalId}_${voter}",
            proposalId = proposalId,
            voterId = voter,
            selectedOptions = listOf(optionRaw.toInt()),
            tokenId = null,
            endorser = null,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )
    }

    private fun processAllStakeholdersVote(event: IndexedEvent): VoteTally? { // Fix function name
        val params = event.params.getReturnValues()
        val proposalId = params["proposalId"]?.toString() ?: return null
        val sender = params["sender"]?.toString() ?: return null
        val endorser = params["endorser"]?.toString() ?: return null
        val tokenId = params["tokenId"]?.toString() ?: return null
        val optionRaw = params["options"]?.toString() ?: return null

        return VoteTally(
            id = "${proposalId}_${sender}",
            proposalId = proposalId,
            voterId = sender,
            selectedOptions = listOf(optionRaw.toInt()),
            tokenId = tokenId,
            endorser = endorser,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )
    }
} // end

private data class ResultClass(val proposalId: String, val selectedOptions: Int, val count: Int)
