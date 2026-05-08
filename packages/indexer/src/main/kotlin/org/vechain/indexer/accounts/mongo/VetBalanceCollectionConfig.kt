package org.vechain.indexer.accounts.mongo

import java.math.BigInteger
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.insert
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.accounts.VetBalance
import org.vechain.indexer.config.genesis.GenesisVetBalanceLoader
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts", "vet-balance")
@Configuration
open class VetBalanceCollectionConfig(
    private val mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    private val genesisVetBalanceLoader: GenesisVetBalanceLoader,
) : CollectionConfig(mongoTemplate, appCoroutineScope, VetBalance::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.vet-balance:1}") private val version: Int = 1

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.VET_BALANCE.NAME,
            VetBalance::class.java,
            version,
        )
        ensureCollection()
        preloadGenesisIfCollectionEmpty()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                // Supports API query by address + timestamp range with default sort by newest.
                buildIndex(
                    VetBalance::address.name to Sort.Direction.ASC,
                    IndexedDocument::blockTimestamp.name to Sort.Direction.DESC,
                ),
            )
        )
    }

    private fun preloadGenesisIfCollectionEmpty() {
        if (collectionHasDocuments()) {
            logger.info(
                "Skipping genesis preload for ${modelObj.simpleName}: collection already has records."
            )
            return
        }
        val genesis = genesisVetBalanceLoader.loadGenesisAllocations()
        if (genesis == null) {
            logger.warn("Skipping genesis preload for ${modelObj.simpleName}: resource not found.")
            return
        }
        val computedTotalSupply =
            genesis.allocations.fold(BigInteger.ZERO) { acc, allocation ->
                acc + BigInteger(allocation.balance)
            }
        val records =
            genesis.allocations.map { allocation ->
                VetBalance(
                    address = allocation.address,
                    blockId = genesis.genesisBlock.id,
                    blockNumber = 0L,
                    blockTimestamp = genesis.genesisBlock.timestamp,
                    balance = BigInteger(allocation.balance),
                )
            }
        mongoTemplate.insert<VetBalance>(records)
        logger.info(
            "Preloaded {} genesis VET balances for network={} (launchTime={}, totalSupply={}).",
            records.size,
            genesis.network,
            genesis.launchTime,
            computedTotalSupply,
        )
    }
}
