package org.vechain.indexer.stargate.vthoClaimed

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult

@Profile("stargate", "vtho-claimed-by-block")
@Component
open class VthoClaimedByBlockProcessor(
    private val service: VthoClaimedByBlockService,
    repository: VthoClaimedByBlockRepository,
) : BaseProcessor(repository = repository, indexerName = IndexerNames.VTHO_CLAIMED_BY_BLOCK) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        val newRecords = service.processEvents(entry.events())

        if (newRecords.isNotEmpty()) {
            service.saveRecords(newRecords)
        }
    }
}
