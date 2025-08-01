package org.vechain.indexer.historical

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("historical-proposals")
@Component
open class HistoricalProposalsProcessor(
    private val repository: HistoricalProposalsRepository,
    private val mongoTemplate: MongoTemplate,
    private val historicalProposalsService: HistoricalProposalsService,
) : BaseProcessor(repository = repository) {

    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) return
        val proposals: List<HistoricalProposals> =
            historicalProposalsService.processNewProposals(matchedEvents)

        if (proposals.isNotEmpty()) {
            repository.saveAll(proposals)
        }
    }

    override fun rollback(blockNumber: Long) {
        repository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
