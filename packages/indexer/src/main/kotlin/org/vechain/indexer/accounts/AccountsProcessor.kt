package org.vechain.indexer.accounts

import jakarta.annotation.PostConstruct
import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.genesis.GenesisVetBalanceLoader
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("accounts")
@Component
open class AccountsProcessor(
    private val overviewService: AccountOverviewService,
    private val totalsService: AccountTotalsSeriesService,
    private val repository: AccountsWriteRepository,
    private val genesisLoader: GenesisVetBalanceLoader,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.accounts:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.ACCOUNTS.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.ACCOUNTS.NAME,
        version,
        processorMetrics,
    ) {

    /** After the version check, an empty schema starts from the genesis VET allocations. */
    @PostConstruct
    override fun bootstrap() {
        super.bootstrap()
        if (repository.hasOverviews()) return
        val genesis = genesisLoader.loadGenesisAllocations()
        if (genesis == null) {
            startupLogger.warn("Skipping genesis preload for accounts: resource not found.")
            return
        }
        val block = genesis.genesisBlock
        repository.saveGenesis(
            genesis.allocations.map {
                AccountOverview(
                    address = it.address,
                    blockId = block.id,
                    blockNumber = 0L,
                    blockTimestamp = block.timestamp,
                    firstSeen = block.timestamp,
                    lastSeen = block.timestamp,
                    vetBalance = BigInteger(it.balance),
                )
            },
            genesis.allocations.map {
                VetBalance(it.address, block.id, 0L, block.timestamp, BigInteger(it.balance))
            },
        )
        startupLogger.info(
            "Preloaded {} genesis accounts for network={} (launchTime={}).",
            genesis.allocations.size,
            genesis.network,
            genesis.launchTime,
        )
    }

    override suspend fun processEntry(entry: IndexingResult) {
        require(entry is IndexingResult.BlockResult) {
            "Expected IndexingResult.BlockResult with full block data"
        }
        val block = entry.block
        if (overviewService.isHayabusaBlock(block.number)) overviewService.settleHayabusa(block)
        val overview = overviewService.processBlock(block, entry.events)
        val totals = totalsService.processBlock(block, entry.events)
        repository.save(
            AccountsUpdate(
                blockNumber = block.number,
                overviews = overview.overviews,
                balances = overview.balances,
                newAccounts = totals.newAccounts,
                totals = totals.totals,
            )
        )
        totalsService.saved(totals.totals)
    }

    override fun resetProcessingState() {
        super.resetProcessingState()
        totalsService.resetCache()
    }
}
