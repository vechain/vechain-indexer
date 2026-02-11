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
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.thor.model.Block

@Profile("accounts", "account-overview")
@Component
open class AccountOverviewProcessor(
    private val service: AccountOverviewService,
    repository: AccountOverviewRepository,
    archiveService: ArchiveService<AccountOverview, AccountOverviewArchive>,
    checkpointService: CheckpointService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = archiveService,
        indexerName = IndexerNames.ACCOUNT_OVERVIEW_INDEXER,
        checkpointService = checkpointService,
        collectionName = "account_overviews",
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
            withContext(Dispatchers.IO) { settleAllAccountsAtHayabusa(block) }
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
    private fun settleAllAccountsAtHayabusa(block: Block) {
        logger.info("Starting Hayabusa VTHO settlement at block {} {}", block.number, block.id)

        var totalSettled = 0
        var hasMore = true

        while (hasMore) {
            val pageable = PageRequest.of(0, HAYABUSA_SETTLEMENT_BATCH_SIZE)
            val batch = service.getAccountsNeedingHayabusaSettlement(block.timestamp, pageable)

            if (batch.content.isNotEmpty()) {
                service.settleHayabusaBatch(batch.content, block.timestamp)
                totalSettled += batch.content.size
                logger.info("Settled {} accounts (total: {})", batch.content.size, totalSettled)
            }

            // Since we're modifying records, always query page 0
            // hasMore is false when no more records match the query
            hasMore = batch.content.isNotEmpty()
        }

        logger.info("Completed Hayabusa VTHO settlement. Total accounts settled: {}", totalSettled)
    }
}
