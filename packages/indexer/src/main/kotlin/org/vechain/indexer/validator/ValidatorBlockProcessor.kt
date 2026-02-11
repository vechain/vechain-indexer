package org.vechain.indexer.validator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService

@Profile("validator", "validator-reward")
@Component
open class ValidatorBlockProcessor(
    private val service: ValidatorBlockService,
    repository: ValidatorBlockRepository,
    checkpointService: CheckpointService,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.VALIDATOR_BLOCK.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.VALIDATOR_BLOCK.COLLECTION,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val newRecords = service.processBlock(entry.block, entry.callResults())

        if (newRecords.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(newRecords) }
        }
    }
}
