package org.vechain.indexer.b3tr.action.mongo

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.b3tr.action.AppDailyActionSummary
import org.vechain.indexer.b3tr.action.AppDailyActionSummaryArchive
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-daily-action-summary")
open class AppDailyActionSummaryCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    @param:Value("\${indexer.version.b3tr-app-daily-action-summary}") private val version: Int,
) : CollectionConfig(mongoTemplate, appCoroutineScope, AppDailyActionSummary::class.java) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                AppDailyActionSummary::class.java,
                version,
            )

        if (dropped) {
            indexerVersionService.dropArchiveCollection(AppDailyActionSummaryArchive::class.java)
        }

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "appId_-1_date_-1" to
                    Index().on("appId", Sort.Direction.DESC).on("date", Sort.Direction.DESC),
                "appId_-1_date_-1_totalRewardAmount_-1" to
                    Index()
                        .on("appId", Sort.Direction.DESC)
                        .on("date", Sort.Direction.DESC)
                        .on("totalRewardAmount", Sort.Direction.DESC),
                "appId_-1_date_-1_actionsRewarded_-1" to
                    Index()
                        .on("appId", Sort.Direction.DESC)
                        .on("date", Sort.Direction.DESC)
                        .on("actionsRewarded", Sort.Direction.DESC),
                "blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
            )
        )
    }
}
