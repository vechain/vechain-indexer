package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.repository.stargate.VthoClaimedByBlockRepository
import org.vechain.indexer.thor.model.Block

@Profile("stargate")
@Component
open class VthoClaimedByBlockProcessor(
    private val service: VthoClaimedByBlockService,
    repository: VthoClaimedByBlockRepository,
) : BaseProcessor(repository) {

    override fun process(events: List<IndexedEvent>, block: Block?) {
        if (events.isEmpty()) {
            return
        }

        val newRecord = service.processEvents(events)

        if (newRecord != null) {
            service.saveRecord(newRecord)
        }
    }
}
