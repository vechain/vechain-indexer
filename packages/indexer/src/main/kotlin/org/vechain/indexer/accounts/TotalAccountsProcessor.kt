package org.vechain.indexer.accounts

import kotlin.collections.isNotEmpty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.repository.TotalAccountsRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("accounts", "total-accounts")
@Component
open class TotalAccountsProcessor(
    private val service: TotalAccountsService,
    repository: TotalAccountsRepository,
    archiveService: ArchiveService<TotalAccounts, TotalAccountsArchive>,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = archiveService,
        indexerName = IndexerNames.TOTAL_ACCOUNTS.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.TOTAL_ACCOUNTS.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }
        val (updated, existing) = service.processBlock(entry.block, entry.callResults())

        if (updated.isNotEmpty()) {
            service.save(updated, existing)
        }
    }
}
