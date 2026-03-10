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

@Profile("explorer", "average-fees-per-user")
@Configuration
open class AverageFeesPerUserCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
    appCoroutineScope: CoroutineScope,
) : CollectionConfig(mongoTemplate, appCoroutineScope, AverageFeesPerUser::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.average-fees-per-user:1}") private val version: Int = 1

    @PostConstruct
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
                "dayStartTimestamp_1" to
                    Index().on(AverageFeesPerUser::dayStartTimestamp.name, Sort.Direction.ASC),
                "date_1" to Index().on(AverageFeesPerUser::date.name, Sort.Direction.ASC),
            )
        )
    }
}
