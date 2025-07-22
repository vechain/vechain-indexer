package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.repository.stargate.VetStakedByBlockRepository
import org.vechain.indexer.thor.model.Block

@Profile("stargate")
@Component
open class VetStakedByBlockProcessor(
    private val service: VetStakedByBlockService,
    repository: VetStakedByBlockRepository,
) : BaseProcessor(repository) {

    override fun process(events: List<IndexedEvent>, block: Block?) {
        if (events.isEmpty()) {
            return
        }

        val newRecords = service.processEvents(events)

        if (newRecords != null) {
            service.saveRecord(newRecords)
        }
    }
}
