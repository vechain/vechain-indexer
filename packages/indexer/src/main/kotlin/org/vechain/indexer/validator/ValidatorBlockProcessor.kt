package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("validator", "validator-reward")
@Component
open class ValidatorBlockProcessor(
    private val service: ValidatorBlockService,
    repository: ValidatorBlockRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VALIDATOR_BLOCK,
    ) {
    override fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val newRecords = service.processBlock(entry.block, entry.callResults())

        if (newRecords.isNotEmpty()) {
            service.save(newRecords)
        }
    }
}
