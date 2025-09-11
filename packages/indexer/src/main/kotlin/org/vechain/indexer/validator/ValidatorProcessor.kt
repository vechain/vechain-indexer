package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.Block

@Profile("validator")
@Component
open class ValidatorProcessor(
    repository: ValidatorRepository,
    private val service: ValidatorService,
    private val thorService: ThorService,
) : BaseProcessor(repository) {
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        println("Processing ${matchedEvents.size} validator events at block ${block?.number}")
        for (event in matchedEvents) {
            println("Event: $event ")
        }
        service.handleValidatorEvents(matchedEvents)

        val bestBlock = thorService.getBestBlock()
        if (bestBlock.number - (block?.number ?: 0) > 25) {
            println(
                "Skipping validator refresh at block ${block?.number}, too far from best block ${bestBlock.number}"
            )
            return
        }

        service.refreshValidators(block!!.id, block.number, block.timestamp)
    }
}
