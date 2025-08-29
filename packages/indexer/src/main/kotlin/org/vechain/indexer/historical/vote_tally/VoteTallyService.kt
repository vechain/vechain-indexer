package org.vechain.indexer.historical.vote_tally

import kotlin.jvm.java
import org.bson.Document
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.ThorService

@Profile("historical-proposals")
@Service
open class VoteTallyService(
    private val thorService: ThorService,
    @Value("\${veworld.contract.historical_proposals.steering_committee}")
    private val steeringCommitteeAddress: String,
    @Value("\${veworld.contract.historical_proposals.all_stakeholders}")
    private val allStakeholdersAddress: String,
    private val mongoTemplate: MongoTemplate,
) {
    private var bestBlockNumber: Long = 0
    private var hasReachedCurrentBlock = false
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun processNewVotes(events: List<IndexedEvent>, currentBlockNumber: Long?): List<VoteTally> {
        logger.info(
            "Processing votes. hasReachedCurrentBlock: $hasReachedCurrentBlock, bestBlockNumber: $bestBlockNumber"
        )

        if (!hasReachedCurrentBlock) {
            val bestBlock = thorService.getBestBlock()
            bestBlockNumber = bestBlock.number
            val blockCheck = currentBlockNumber ?: events.firstOrNull()?.blockNumber

            logger.info("Block check: $blockCheck, bestBlockNumber: $bestBlockNumber")

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
                when (event.address.toString().lowercase()) {
                    steeringCommitteeAddress.lowercase() -> processSteeringCommitteeVote(event)
                    allStakeholdersAddress.lowercase() -> processAllStakeholdersVote(event)
                    else -> {
                        logger.debug("Skipping event, as address found is: ${event.address}")
                        null
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing event: ${e.message}", e)
                null
            }
        }
    }

    private fun aggregateAndUpdateProposals() {
        logger.info("Starting vote tally aggregation...")
        val startTime = System.currentTimeMillis()

        val pipeline =
            listOf(
                // unwind the already decoded selectedOptions array
                """
            {
                ${'$'}unwind: "${'$'}selectedOptions"
            }
            """,
                // Group by proposalId and option to count votes
                """
            {
                ${'$'}group: {
                    _id: {
                        proposalId: "${'$'}proposalId",
                        option: "${'$'}selectedOptions"
                    },
                    count: { ${'$'}sum: 1 }
                }
            }
            """,
                // Reshape the output
                """
            {
                ${'$'}project: {
                    _id: 0,
                    proposalId: "${'$'}_id.proposalId",
                    option: "${'$'}_id.option",
                    count: 1
                }
            }
            """,
            )

        // Execute the aggregation using the raw pipeline above
        val results =
            mongoTemplate
                .getCollection("historical-vote-tally")
                .aggregate(pipeline.map { Document.parse(it) })
                .into(ArrayList<Document>())
                .map {
                    ResultClass(
                        it.getString("proposalId"),
                        it.getInteger("option"),
                        it.getInteger("count"),
                    )
                }

        val duration = System.currentTimeMillis() - startTime
        logger.info("Vote tallying complete. Processed ${results.size} results in ${duration}ms")

        // Group results by proposalId for easier processing
        val resultsByProposal = results.groupBy { it.proposalId }

        // Now update the tallies
        resultsByProposal.forEach { (proposalId, optionResults) ->
            val optionCounts = optionResults.associate { it.option to it.count }

            // Update all VoteTally documents for this proposal with the tally
            val query = Query.query(Criteria.where("proposalId").`is`(proposalId))
            val update = Update().set("tally", optionCounts)

            mongoTemplate.updateMulti(query, update, VoteTally::class.java)
            logger.info("Updated tally for proposal $proposalId in VoteTally documents")
        }
    }

    private fun processSteeringCommitteeVote(event: IndexedEvent): VoteTally? {
        val param = event.params.getReturnValues()
        val proposalId = param["proposalId"]?.toString() ?: return null
        val voter = param["voter"]?.toString() ?: return null
        val optionRaw = param["options"]?.toString() ?: return null
        val optionValue = optionRaw.toInt()

        val selectedOptions = decodeOptions(optionValue)
        val weight = selectedOptions.size

        return VoteTally(
            id = "${proposalId}_${voter}",
            proposalId = proposalId,
            voterId = voter,
            selectedOptions = selectedOptions,
            tokenId = null,
            endorser = null,
            weight = weight,
            tally = null,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )
    }

    private fun processAllStakeholdersVote(event: IndexedEvent): VoteTally? {
        val params = event.params.getReturnValues()
        val proposalId = params["proposalId"]?.toString() ?: return null
        val sender = params["sender"]?.toString() ?: return null
        val endorser = params["endorser"]?.toString() ?: return null
        val tokenId = params["tokenId"]?.toString() ?: return null
        val optionRaw = params["options"]?.toString() ?: return null
        val optionValue = optionRaw.toInt()

        val selectedOptions = decodeOptions(optionValue)
        val weight = selectedOptions.size

        return VoteTally(
            id = "${proposalId}_${sender}",
            proposalId = proposalId,
            voterId = sender,
            selectedOptions = selectedOptions,
            tokenId = tokenId,
            endorser = endorser,
            weight = weight,
            tally = null,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )
    }

    // Helper function to decode options bitmask
    private fun decodeOptions(optionValue: Int): List<Int> {
        val selectedOptions = mutableListOf<Int>()
        for (i in 0 until 32) {
            if ((optionValue and (1 shl i)) != 0) {
                selectedOptions.add(i + 1)
            }
        }
        return selectedOptions
    }
}

private data class ResultClass(val proposalId: String, val option: Int, val count: Int)
