package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("validator", "validator-stats")
@Component
open class ValidatorProcessor(
    repository: ValidatorRepository,
    mongoTemplate: MongoTemplate,
    checkpointService: CheckpointService,
    private val service: ValidatorService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.VALIDATOR.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.VALIDATOR.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException(
                "Expected IndexingResult.BlockResult (full block result) but got ${entry::class.simpleName}"
            )
        }

        val (updated, existing) =
            service.processBlock(entry.block, entry.events(), entry.callResults)

        if (updated.isNotEmpty()) {
            service.save(updated, existing)
        }
    }
}
