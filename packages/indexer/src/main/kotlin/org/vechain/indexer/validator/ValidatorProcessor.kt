package org.vechain.indexer.validator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.version.IndexerVersionService

@Profile("validator", "validator-stats")
@Component
open class ValidatorProcessor(
    repository: ValidatorRepository,
    private val service: ValidatorService,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VALIDATOR,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        // Initialize queue positions from contract if any queued validators still have null
        // positions
        service.initializeQueuePositionsIfNeeded(entry.block.id)

        val (updated, existing) =
            service.processBlock(
                entry.block,
                entry.events(),
                entry.callResults,
                entry.status == Status.FULLY_SYNCED,
            )

        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
