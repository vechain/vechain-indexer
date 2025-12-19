package org.vechain.indexer.accounts.mongo

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.accounts.AccountOverview
import org.vechain.indexer.accounts.AccountOverviewArchive
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts", "account-overview")
@Configuration
open class AccountOverviewCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
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

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        // Ensure indexes
        // Currently it'll just be a lookup by address (the ID), so no extra indexes needed
    }
}
