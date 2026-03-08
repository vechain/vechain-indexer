package org.vechain.indexer.explorer

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
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("explorer", "block-usage")
@Configuration
open class BlockUsageCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
    appCoroutineScope: CoroutineScope,
) : CollectionConfig(mongoTemplate, appCoroutineScope, BlockUsage::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.block-usage:1}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.BLOCK_USAGE.NAME,
            BlockUsage::class.java,
            version,
        )

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        // Startup rollback and getLatestRecord() both operate on blockNumber.
        ensureRequiredIndex(
            "blockNumber_-1",
            Index().on(BlockUsage::blockNumber.name, Sort.Direction.DESC),
        )

        ensureIndexes(
            listOf(
                // Index for findAllInTimestampRange - range queries on blockTimestamp
                "blockTimestamp_1" to
                    Index().on(BlockUsage::blockTimestamp.name, Sort.Direction.ASC),
                // Index for findHourlyInTimestampRange - filter by isHourly, then range by
                // blockTimestamp
                "isHourly_1_blockTimestamp_1" to
                    Index()
                        .on(BlockUsage::isHourly.name, Sort.Direction.ASC)
                        .on(BlockUsage::blockTimestamp.name, Sort.Direction.ASC),
                // Index for findDailyInTimestampRange - filter by isDaily, then range by
                // blockTimestamp
                "isDaily_1_blockTimestamp_1" to
                    Index()
                        .on(BlockUsage::isDaily.name, Sort.Direction.ASC)
                        .on(BlockUsage::blockTimestamp.name, Sort.Direction.ASC),
                // Index for findWeeklyInTimestampRange - filter by isWeekly, then range by
                // blockTimestamp
                "isWeekly_1_blockTimestamp_1" to
                    Index()
                        .on(BlockUsage::isWeekly.name, Sort.Direction.ASC)
                        .on(BlockUsage::blockTimestamp.name, Sort.Direction.ASC),
                // Index for findMonthlyInTimestampRange - filter by isMonthly, then range by
                // blockTimestamp
                "isMonthly_1_blockTimestamp_1" to
                    Index()
                        .on(BlockUsage::isMonthly.name, Sort.Direction.ASC)
                        .on(BlockUsage::blockTimestamp.name, Sort.Direction.ASC),
            )
        )
    }
}
