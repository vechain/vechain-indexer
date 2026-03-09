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

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.ACCOUNT_TOTALS_SERIES.NAME,
            AccountTotalsSeries::class.java,
            version,
        )

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "blockNumber_-1" to
                    Index().on(AccountTotalsSeries::blockNumber.name, Sort.Direction.DESC),
                "recordType_1_blockTimestamp_1" to
                    Index()
                        .on(AccountTotalsSeries::recordType.name, Sort.Direction.ASC)
                        .on(AccountTotalsSeries::blockTimestamp.name, Sort.Direction.ASC),
                "recordType_1_isHourly_1_blockTimestamp_1" to
                    Index()
                        .on(AccountTotalsSeries::recordType.name, Sort.Direction.ASC)
                        .on(AccountTotalsSeries::isHourly.name, Sort.Direction.ASC)
                        .on(AccountTotalsSeries::blockTimestamp.name, Sort.Direction.ASC),
                "recordType_1_isDaily_1_blockTimestamp_1" to
                    Index()
                        .on(AccountTotalsSeries::recordType.name, Sort.Direction.ASC)
                        .on(AccountTotalsSeries::isDaily.name, Sort.Direction.ASC)
                        .on(AccountTotalsSeries::blockTimestamp.name, Sort.Direction.ASC),
                "recordType_1_isWeekly_1_blockTimestamp_1" to
                    Index()
                        .on(AccountTotalsSeries::recordType.name, Sort.Direction.ASC)
                        .on(AccountTotalsSeries::isWeekly.name, Sort.Direction.ASC)
                        .on(AccountTotalsSeries::blockTimestamp.name, Sort.Direction.ASC),
                "recordType_1_isMonthly_1_blockTimestamp_1" to
                    Index()
                        .on(AccountTotalsSeries::recordType.name, Sort.Direction.ASC)
                        .on(AccountTotalsSeries::isMonthly.name, Sort.Direction.ASC)
                        .on(AccountTotalsSeries::blockTimestamp.name, Sort.Direction.ASC),
                "recordType_1_address_1" to
                    Index()
                        .on(AccountTotalsSeries::recordType.name, Sort.Direction.ASC)
                        .on(AccountTotalsSeries::address.name, Sort.Direction.ASC),
            )
        )
    }
}
