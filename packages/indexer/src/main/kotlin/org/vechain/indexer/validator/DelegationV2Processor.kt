package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("delegation-v2")
@Component
open class DelegationV2Processor(
    repository: DelegationV2Repository,
    mongoTemplate: MongoTemplate,
    checkpointService: CheckpointService,
    private val service: DelegationV2Service,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.DELEGATION_V2.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.DELEGATION_V2.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException(
                "Expected IndexingResult.BlockResult but got ${entry::class.simpleName}"
            )
        }

        val (updated, archived) = service.processBlock(entry.block, entry.events())

        if (updated.isNotEmpty()) {
            service.save(updated, archived)
        }
    }

    /**
     * Called by [org.vechain.indexer.BaseStatefulProcessor.rollback] on reorg. The service holds an
     * in-memory mirror of zero-cycle delegations — drop it so the next block reloads from the
     * (now-rolled-back) database state instead of carrying entries from the reorged branch.
     */
    override fun resetProcessingState() {
        service.invalidateCache()
    }
}
