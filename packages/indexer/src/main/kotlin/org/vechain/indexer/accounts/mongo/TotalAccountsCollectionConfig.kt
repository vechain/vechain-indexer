package org.vechain.indexer.accounts.mongo

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.accounts.TotalAccounts
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts", "total-accounts")
@Configuration
open class TotalAccountsCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        TotalAccounts::class.java,
        hasArchives = true,
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.total-accounts}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.TOTAL_ACCOUNTS.NAME,
            TotalAccounts::class.java,
            version,
        )

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        // Ensure indexes
        ensureIndexes(
            listOf(
                "blockTimestamp_-1" to
                    Index().on(IndexedDocument::blockTimestamp.name, Sort.Direction.DESC),
                "timeFrame_1_blockTimestamp_-1" to
                    Index()
                        .on(TotalAccounts::timeFrame.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockTimestamp.name, Sort.Direction.DESC),
                "blockNumber_1" to Index().on(IndexedDocument::blockNumber.name, Sort.Direction.ASC),
            )
        )
    }
}
