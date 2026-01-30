package org.vechain.indexer.accounts.mongo

import jakarta.annotation.PostConstruct
import java.math.BigInteger
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.count
import org.springframework.data.mongodb.core.insert
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.accounts.AccountOverview
import org.vechain.indexer.accounts.AccountOverviewArchive
import org.vechain.indexer.config.genesis.GenesisVetBalanceLoader
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts", "account-overview")
@Configuration
open class AccountOverviewCollectionConfig(
    private val mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    private val genesisVetBalanceLoader: GenesisVetBalanceLoader,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        AccountOverview::class.java,
        AccountOverviewArchive::class.java,
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.account-overview}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                indexerName = IndexerNames.ACCOUNT_OVERVIEW_INDEXER,
                AccountOverview::class.java,
                version,
            )

        if (dropped) indexerVersionService.dropArchiveCollection(AccountOverviewArchive::class.java)

        ensureCollection()

        preloadGenesisIfCollectionEmpty()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        // Ensure indexes
        // Currently it'll just be a lookup by address (the ID), so no extra indexes needed
    }

    private fun preloadGenesisIfCollectionEmpty() {
        val existingCount = mongoTemplate.count<AccountOverview>(Query())
        if (existingCount > 0) {
            logger.info(
                "Skipping genesis preload for ${modelObj.simpleName}: collection already has {} records.",
                existingCount,
            )
            return
        }

        val genesis = genesisVetBalanceLoader.loadGenesisAllocations()
        if (genesis == null) {
            logger.warn("Skipping genesis preload for ${modelObj.simpleName}: resource not found.")
            return
        }

        val records =
            genesis.allocations.map { allocation ->
                AccountOverview(
                    address = allocation.address,
                    blockId = genesis.genesisBlock.id,
                    blockNumber = 0L,
                    blockTimestamp = genesis.genesisBlock.timestamp,
                    version = 0,
                    firstSeen = genesis.genesisBlock.timestamp,
                    lastSeen = genesis.genesisBlock.timestamp,
                    transactionsSent = 0L,
                    clausesSent = 0L,
                    vthoBurned = BigInteger.ZERO,
                    vthoDelegated = BigInteger.ZERO,
                    gasUsed = BigInteger.ZERO,
                    vetSent = BigInteger.ZERO,
                    vetReceived = BigInteger.ZERO,
                    vetBalance = BigInteger(allocation.balance),
                    vthoBlockRewards = BigInteger.ZERO,
                    vthoPassiveGeneration = BigInteger.ZERO,
                )
            }

        mongoTemplate.insert<AccountOverview>(records)
        logger.info(
            "Preloaded {} genesis account overviews for network={} (launchTime={}).",
            records.size,
            genesis.network,
            genesis.launchTime,
        )
    }
}
