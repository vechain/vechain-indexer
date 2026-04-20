package org.vechain.indexer.b3tr.navigator

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-navigator", "b3tr-navigator-fee-summary")
@Component
open class NavigatorFeeSummaryProcessor(
    repository: NavigatorFeeSummaryRepository,
    mongoTemplate: MongoTemplate,
    private val service: NavigatorFeeSummaryService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.NAVIGATOR_FEE_SUMMARY.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.NAVIGATOR_FEE_SUMMARY.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        val accumulator =
            VersionedDocumentAccumulator<NavigatorFeeSummaryDocument>(service::findById)
        if (entry is IndexingResult.BlockResult) {
            accumulator.startBlock()
            if (entry.events().isNotEmpty()) {
                service.processBlockEvents(
                    entry.events(),
                    BlockDetails(entry.block.id, entry.block.number, entry.block.timestamp),
                    accumulator,
                )
            }
        } else if (entry.events().isNotEmpty()) {
            groupByBlock(entry.events()).forEach { (blockDetails, blockEvents) ->
                accumulator.startBlock()
                service.processBlockEvents(blockEvents, blockDetails, accumulator)
            }
        }

        val (updated, archives) = accumulator.results()
        if (updated.isNotEmpty() || archives.isNotEmpty()) {
            service.save(updated, archives)
        }
    }
}
