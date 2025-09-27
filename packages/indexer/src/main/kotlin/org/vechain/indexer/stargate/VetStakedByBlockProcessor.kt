package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult

@Profile("stargate", "vet-staked-by-block")
@Component
open class VetStakedByBlockProcessor(
    private val service: VetStakedByBlockService,
    repository: VetStakedByBlockRepository,
) : BaseProcessor(repository) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        val newRecords = service.processEvents(entry.events())

        if (newRecords.isNotEmpty()) {
            service.saveRecords(newRecords)
        }
    }
}
