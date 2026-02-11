package org.vechain.indexer.stargate.vetStaked

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService

@Profile("stargate", "vet-staked-by-block")
@Component
open class VetStakedByBlockProcessor(
    private val service: VetStakedByBlockService,
    repository: VetStakedByBlockRepository,
    checkpointService: CheckpointService,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.VET_STAKED_BY_BLOCK,
        checkpointService = checkpointService,
        collectionName = "stargate_total_vet_staked_by_block",
    ) {
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
