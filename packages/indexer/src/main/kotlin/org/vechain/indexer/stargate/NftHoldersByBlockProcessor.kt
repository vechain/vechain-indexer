package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timing.WithTiming

@Profile("stargate", "nft-holders-by-block")
@Component
open class NftHoldersByBlockProcessor(
    private val service: NftHoldersByBlockService,
    repository: NftHoldersByBlockRepository,
) : BaseProcessor(repository) {

    @WithTiming("NftHoldersByBlockProcessor.process")
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }

        val newRecords = service.processEvents(matchedEvents)

        if (newRecords.isNotEmpty()) {
            service.saveRecords(newRecords)
        }
    }
}
