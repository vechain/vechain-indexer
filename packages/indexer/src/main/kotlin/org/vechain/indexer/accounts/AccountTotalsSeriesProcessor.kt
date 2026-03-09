package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.repository.AccountTotalsSeriesRepository
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("accounts", "account-totals-series")
@Component
open class AccountTotalsSeriesProcessor(
    private val service: AccountTotalsSeriesService,
    repository: AccountTotalsSeriesRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.ACCOUNT_TOTALS_SERIES.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.ACCOUNT_TOTALS_SERIES.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException(
                "Expected IndexingResult.BlockResult with full block data"
            )
        }

        val records = service.processBlock(entry.block)
        service.save(records)
    }

    override fun rollback(blockNumber: Long) {
        service.clearProcessingState()
        super.rollback(blockNumber)
    }
}
