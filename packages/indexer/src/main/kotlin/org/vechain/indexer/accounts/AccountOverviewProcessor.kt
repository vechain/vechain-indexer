package org.vechain.indexer.accounts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts", "account-overview")
@Component
open class AccountOverviewProcessor(
    private val service: AccountOverviewService,
    repository: AccountOverviewRepository,
    archiveService: ArchiveService<AccountOverview, AccountOverviewArchive>,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = archiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.ACCOUNT_OVERVIEW_INDEXER,
    ) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val HAYABUSA_SETTLEMENT_BATCH_SIZE = 1000
    }

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val block = entry.block

        // At the Hayabusa fork block, settle passive VTHO for all accounts with VET balance
        if (service.isHayabusaBlock(block.number)) {
            withContext(Dispatchers.IO) { settleAllAccountsAtHayabusa(block.timestamp) }
        }

        val (updated, existing) = service.processBlock(block, entry.events)

        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }

    /**
     * Settle passive VTHO for all accounts at the Hayabusa fork in batches. Each batch is processed
     * in its own transaction to avoid memory issues.
     */
    private fun settleAllAccountsAtHayabusa(hayabusaTimestamp: Long) {
        logger.info("Starting Hayabusa passive VTHO settlement at timestamp {}", hayabusaTimestamp)

        var totalSettled = 0
        var hasMore = true

        while (hasMore) {
            val pageable = PageRequest.of(0, HAYABUSA_SETTLEMENT_BATCH_SIZE)
            val batch = service.getAccountsNeedingHayabusaSettlement(hayabusaTimestamp, pageable)

            if (batch.content.isNotEmpty()) {
                service.settleHayabusaBatch(batch.content, hayabusaTimestamp)
                totalSettled += batch.content.size
                logger.info("Settled {} accounts (total: {})", batch.content.size, totalSettled)
            }

            // Since we're modifying records, always query page 0
            // hasMore is false when no more records match the query
            hasMore = batch.content.isNotEmpty()
        }

        logger.info(
            "Completed Hayabusa passive VTHO settlement. Total accounts settled: {}",
            totalSettled,
        )
    }
}
