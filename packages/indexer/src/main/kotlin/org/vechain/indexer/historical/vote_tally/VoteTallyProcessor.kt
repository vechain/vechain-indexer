package org.vechain.indexer.historical.vote_tally

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("historical-proposals")
@Component
open class VoteTallyProcessor(
    private val repository: VoteTallyRepository,
    private val voteTallyService: VoteTallyService,
) : BaseProcessor(repository = repository) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }
        val votes = voteTallyService.processNewVotes(matchedEvents, block?.number)
        logger.info("VoteTallyProcessor: Got ${votes.size} votes to save")
        if (votes.isNotEmpty()) {
            try {
                repository.saveAll(votes)
                logger.info("VoteTallyProcessor: Saved ${votes.size} votes")
            } catch (e: Exception) {
                logger.error("Error saving votes: ${e.message}", e)
            }
        }
    }

    override fun rollback(blockNumber: Long) {
        repository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
