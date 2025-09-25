package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timing.WithTiming

@Profile("stargate", "vtho-claimed-by-block")
@Component
open class VthoClaimedByBlockProcessor(
    private val service: VthoClaimedByBlockService,
    repository: VthoClaimedByBlockRepository,
) : BaseProcessor(repository) {

    @WithTiming("VthoClaimedByBlockProcessor.process")
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
