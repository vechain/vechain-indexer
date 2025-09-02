package org.vechain.indexer.historical.vote_tally

import jakarta.annotation.PostConstruct
import kotlin.jvm.java
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.historical.HistoricalProposals
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
    @Value("\${indexer.historical_proposals.triggerAggregation:false}")
    private var shouldTriggerAggregation: Boolean = false,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun init() {
        logger.info(
            "VoteTallyService initialized with shouldTriggerAggregation: $shouldTriggerAggregation"
        )
        if (shouldTriggerAggregation) {
            logger.info("Manually triggering aggregation")
            aggregateAndUpdateProposals()
        }
    }

    fun processNewVotes(events: List<IndexedEvent>, currentBlockNumber: Long?): List<VoteTally> {
        logger.info("Processing ${events.size} votes")

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

    fun updateProposalTallies(proposalId: String) {
        logger.info("Updating tallies for proposal $proposalId")

        // Get all votes for this proposal
        val votes =
            mongoTemplate.find(
                Query.query(Criteria.where("proposalId").`is`(proposalId)),
                VoteTally::class.java,
            )

        if (votes.isEmpty()) {
            logger.info("No votes found for proposal $proposalId")
            return
        }

        // Get the first vote to determine the contract address
        val firstVote = votes.first()
        val contractAddress =
            when {
                firstVote.endorser != null -> allStakeholdersAddress
                else -> steeringCommitteeAddress
            }

        val proposalFullId = "$contractAddress-$proposalId".lowercase()

        // Get the proposal object
        val proposal =
            mongoTemplate.findOne(
                Query.query(Criteria.where("_id").`is`(proposalFullId)),
                HistoricalProposals::class.java,
            )

        if (proposal == null) {
            logger.warn("No proposal found with ID $proposalFullId")
            return
        }

        val tallies = calculateTalliesFromVotes(votes, proposal)
        logger.info(
            "Calculated tallies for proposal $proposalId: ${tallies.tallies}, total: ${tallies.total}"
        )

        try {
            mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").`is`(proposalFullId)),
                Update().set("voteTallies", tallies.tallies).set("totalVotes", tallies.total),
                HistoricalProposals::class.java,
            )
            logger.info("Successfully updated tallies for proposal $proposalId")
        } catch (e: Exception) {
            logger.error("Error updating proposal $proposalId: ${e.message}", e)
        }
    }

    fun aggregateAndUpdateProposals() {
        logger.info("Starting vote tally aggregation...")
        val startTime = System.currentTimeMillis()

        val count = mongoTemplate.getCollection("historical-vote-tally").countDocuments()
        logger.info("Found $count documents in historical-vote-tally collection")

        if (count == 0L) {
            logger.warn("No data to aggregate, skipping")
            return
        }

        // Get all proposals
        val proposals = mongoTemplate.findAll(HistoricalProposals::class.java)
        logger.info("Found ${proposals.size} proposals to update")

        proposals.forEach { proposal ->
            // Get all votes for this proposal
            val votes =
                mongoTemplate.find(
                    Query.query(Criteria.where("proposalId").`is`(proposal.proposalId)),
                    VoteTally::class.java,
                )

            if (votes.isNotEmpty()) {
                val tallies = calculateTalliesFromVotes(votes, proposal)

                try {
                    mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").`is`(proposal.id)),
                        Update()
                            .set("voteTallies", tallies.tallies)
                            .set("totalVotes", tallies.total),
                        HistoricalProposals::class.java,
                    )
                    logger.info("Updated tallies for proposal ${proposal.proposalId}")
                } catch (e: Exception) {
                    logger.error("Error updating proposal ${proposal.proposalId}: ${e.message}", e)
                }
            }
        }

        val duration = System.currentTimeMillis() - startTime
        logger.info("Vote tallying complete in ${duration}ms")
    }

    private fun calculateTalliesFromVotes(
        votes: List<VoteTally>,
        proposal: HistoricalProposals,
    ): TallyResult {
        // Get number of choices from proposal
        val numChoices = proposal.choices?.size ?: 0
        if (numChoices == 0) {
            logger.warn("Proposal ${proposal.proposalId} has no choices")
            return TallyResult(emptyList(), 0)
        }

        // Create array with size equal to number of choices
        val tallies = MutableList(numChoices) { 0 }

        // Count votes for each option
        votes.forEach { vote ->
            vote.selectedOptions.forEach { option ->
                // Adjust for 0-based index since options start at 1
                val index = option - 1
                if (index in tallies.indices) {
                    tallies[index] = tallies[index] + 1
                }
            }
        }

        return TallyResult(tallies = tallies, total = votes.size)
    }

    private fun processSteeringCommitteeVote(event: IndexedEvent): VoteTally? {
        val param = event.params.getReturnValues()
        val proposalId = param["proposalId"]?.toString() ?: return null
        val voter = param["voter"]?.toString() ?: return null
        val optionRaw = param["options"]?.toString() ?: return null
        val optionValue = optionRaw.toLong()
        val selectedOptions = decodeOptions(optionValue)

        return VoteTally(
            id = "${proposalId}_${voter}",
            proposalId = proposalId,
            voterId = voter,
            selectedOptions = selectedOptions,
            tokenId = null,
            endorser = null,
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
        val optionValue = optionRaw.toLong()
        val selectedOptions = decodeOptions(optionValue)

        return VoteTally(
            id = "${proposalId}_${sender}",
            proposalId = proposalId,
            voterId = sender,
            selectedOptions = selectedOptions,
            tokenId = tokenId,
            endorser = endorser,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )
    }

    private fun decodeOptions(optionValue: Long): List<Int> {
        val selectedOptions = mutableListOf<Int>()
        for (i in 0 until 32) {
            if ((optionValue and (1L shl i)) != 0L) {
                selectedOptions.add(i + 1)
            }
        }
        return selectedOptions
    }
}

private data class TallyResult(val tallies: List<Int>, val total: Int)

private data class ResultClass(val proposalId: String, val option: Int, val count: Int)
