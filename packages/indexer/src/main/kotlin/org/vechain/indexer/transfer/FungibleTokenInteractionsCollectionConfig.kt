package org.vechain.indexer.transfer

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("transfers", "fungible-token-interactions")
@Configuration
open class FungibleTokenInteractionsCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, FungibleTokenInteraction::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.fungible-token-interactions}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.FUNGIBLE_TOKEN_INTERACTIONS.NAME,
            FungibleTokenInteraction::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                // For getLatestRecord() and deleteAllByBlockNumberGreaterThanEqual()
                "blockNumber_-1" to
                    Index().on(FungibleTokenInteraction::blockNumber.name, Sort.Direction.DESC),
                "walletAddress_1" to
                    Index().on(FungibleTokenInteraction::walletAddress.name, Sort.Direction.ASC),
                "contractAddress_1_walletAddress_1" to
                    Index()
                        .on(FungibleTokenInteraction::contractAddress.name, Sort.Direction.ASC)
                        .on(FungibleTokenInteraction::walletAddress.name, Sort.Direction.ASC),
            )
        )
    }
}
