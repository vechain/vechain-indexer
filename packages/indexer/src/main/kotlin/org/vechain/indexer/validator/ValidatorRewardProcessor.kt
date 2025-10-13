package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("validator", "validator-reward")
@Component
open class ValidatorRewardProcessor(
    private val service: ValidatorRewardService,
    repository: ValidatorRewardRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VALIDATOR_REWARD,
    ) {
    override fun process(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val newRecord = service.processBlock(entry.block, entry.callResults())

        if (newRecord != null) {
            service.save(newRecord)
        }
    }
}
