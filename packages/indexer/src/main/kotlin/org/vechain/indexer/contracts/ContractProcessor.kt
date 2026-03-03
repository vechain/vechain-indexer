package org.vechain.indexer.contracts

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.contracts.repository.ContractRepository

@Profile("contracts", "contract")
@Component
open class ContractProcessor(
    private val service: ContractService,
    repository: ContractRepository,
    mongoTemplate: MongoTemplate,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.CONTRACTS.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.CONTRACTS.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }
        val (updated, existing) = service.processBlock(entry.events())

        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            service.save(updated, existing)
        }
    }
}
