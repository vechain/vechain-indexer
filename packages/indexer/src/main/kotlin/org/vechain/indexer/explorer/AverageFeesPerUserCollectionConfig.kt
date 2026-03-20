package org.vechain.indexer.explorer

import kotlinx.coroutines.CoroutineScope
import org.bson.Document
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

@Profile("explorer", "average-fees-per-user")
@Configuration
open class AverageFeesPerUserCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
    appCoroutineScope: CoroutineScope,
) : CollectionConfig(mongoTemplate, appCoroutineScope, AverageFeesPerUser::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.average-fees-per-user:1}") private val version: Int = 1
    private val summaryRangeIndexFilter =
        Document(AverageFeesPerUser::recordType.name, AverageFeesPerUserRecordType.SUMMARY.name)
            .append(AverageFeesPerUser::dayStartTimestamp.name, Document("\$exists", true))

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.AVERAGE_FEES_PER_USER.NAME,
            AverageFeesPerUser::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                "blockNumber_-1" to
                    Index().on(AverageFeesPerUser::blockNumber.name, Sort.Direction.DESC),
                "recordType_1_blockNumber_-1" to
                    Index()
                        .on(AverageFeesPerUser::recordType.name, Sort.Direction.ASC)
                        .on(AverageFeesPerUser::blockNumber.name, Sort.Direction.DESC),
                "recordType_1_date_1" to
                    Index()
                        .on(AverageFeesPerUser::recordType.name, Sort.Direction.ASC)
                        .on(AverageFeesPerUser::date.name, Sort.Direction.ASC),
            )
        )
        // This endpoint only reads SUMMARY rows by UTC day range, so give it a dedicated partial
        // index instead of relying on the generic checkpoint exclusion to make the broad index
        // usable.
        ensureIndexes(
            listOf(
                "recordType_1_dayStartTimestamp_1_summary_only" to
                    Index()
                        .on(AverageFeesPerUser::recordType.name, Sort.Direction.ASC)
                        .on(AverageFeesPerUser::dayStartTimestamp.name, Sort.Direction.ASC)
            ),
            partialFilter = summaryRangeIndexFilter,
        )
    }
}
