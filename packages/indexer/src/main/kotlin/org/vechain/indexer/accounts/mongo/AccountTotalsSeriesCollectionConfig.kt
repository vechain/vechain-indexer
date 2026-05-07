package org.vechain.indexer.accounts.mongo

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.accounts.AccountTotalsSeries
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts", "account-totals-series")
@Configuration
open class AccountTotalsSeriesCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
    appCoroutineScope: CoroutineScope,
) : CollectionConfig(mongoTemplate, appCoroutineScope, AccountTotalsSeries::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.account-totals-series:1}") private val version: Int = 1

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.ACCOUNT_TOTALS_SERIES.NAME,
            AccountTotalsSeries::class.java,
            version,
        )
        ensureCollection()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(
                    AccountTotalsSeries::recordType.name to Sort.Direction.ASC,
                    AccountTotalsSeries::blockNumber.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    AccountTotalsSeries::recordType.name to Sort.Direction.ASC,
                    AccountTotalsSeries::blockTimestamp.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AccountTotalsSeries::recordType.name to Sort.Direction.ASC,
                    AccountTotalsSeries::isHourly.name to Sort.Direction.ASC,
                    AccountTotalsSeries::blockTimestamp.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AccountTotalsSeries::recordType.name to Sort.Direction.ASC,
                    AccountTotalsSeries::isDaily.name to Sort.Direction.ASC,
                    AccountTotalsSeries::blockTimestamp.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AccountTotalsSeries::recordType.name to Sort.Direction.ASC,
                    AccountTotalsSeries::isWeekly.name to Sort.Direction.ASC,
                    AccountTotalsSeries::blockTimestamp.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AccountTotalsSeries::recordType.name to Sort.Direction.ASC,
                    AccountTotalsSeries::isMonthly.name to Sort.Direction.ASC,
                    AccountTotalsSeries::blockTimestamp.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AccountTotalsSeries::recordType.name to Sort.Direction.ASC,
                    AccountTotalsSeries::address.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
