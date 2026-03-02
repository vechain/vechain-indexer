package org.vechain.indexer.b3tr.balance

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.b3tr.balance.repository.B3trBalanceRepository
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-balance")
@Component
open class B3trBalanceProcessor(
    private val service: B3trBalanceService,
    repository: B3trBalanceRepository,
    mongoTemplate: MongoTemplate,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.B3TR_BALANCE.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.B3TR_BALANCE.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry is IndexingResult.BlockResult) {
            val nonRevertedTxIds =
                entry.block.transactions.filter { !it.reverted }.map { it.id }.toSet()
            val confirmedEvents = entry.events.filter { it.txId in nonRevertedTxIds }
            val (updated, existing) = service.processBlock(entry.block, confirmedEvents)
            if (updated.isNotEmpty() || existing.isNotEmpty()) {
                service.save(updated, existing)
            }
        } else if (entry.events().isNotEmpty()) {
            groupByBlock(entry.events()).forEach { (blockDetails, blockEvents) ->
                val (updated, existing) = service.processBlock(blockDetails, blockEvents)
                if (updated.isNotEmpty() || existing.isNotEmpty()) {
                    service.save(updated, existing)
                }
            }
        }
    }
}
