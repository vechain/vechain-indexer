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
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.action.AppDailyActionSummary
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

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.APP_DAILY_ACTION_SUMMARY.NAME,
            AppDailyActionSummary::class.java,
            version,
        )

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "user_-1_date_-1" to
                    Index()
                        .on(AppDailyActionSummary::user.name, Sort.Direction.DESC)
                        .on(AppDailyActionSummary::date.name, Sort.Direction.DESC),
                "blockNumber_-1" to
                    Index().on(AppDailyActionSummary::blockNumber.name, Sort.Direction.DESC),
                "appId_1_date_1_totalRewardAmount_-1_user_1" to
                    Index()
                        .on(AppDailyActionSummary::appId.name, Sort.Direction.ASC)
                        .on(AppDailyActionSummary::date.name, Sort.Direction.ASC)
                        .on(AppDailyActionSummary::totalRewardAmount.name, Sort.Direction.DESC)
                        .on(AppDailyActionSummary::user.name, Sort.Direction.ASC),
                "appId_1_date_1_totalRewardAmount_1_user_1" to
                    Index()
                        .on(AppDailyActionSummary::appId.name, Sort.Direction.ASC)
                        .on(AppDailyActionSummary::date.name, Sort.Direction.ASC)
                        .on(AppDailyActionSummary::totalRewardAmount.name, Sort.Direction.ASC)
                        .on(AppDailyActionSummary::user.name, Sort.Direction.ASC),
                "appId_1_date_1_actionsRewarded_-1_user_1" to
                    Index()
                        .on(AppDailyActionSummary::appId.name, Sort.Direction.ASC)
                        .on(AppDailyActionSummary::date.name, Sort.Direction.ASC)
                        .on(AppDailyActionSummary::actionsRewarded.name, Sort.Direction.DESC)
                        .on(AppDailyActionSummary::user.name, Sort.Direction.ASC),
                "appId_1_date_1_actionsRewarded_1_user_1" to
                    Index()
                        .on(AppDailyActionSummary::appId.name, Sort.Direction.ASC)
                        .on(AppDailyActionSummary::date.name, Sort.Direction.ASC)
                        .on(AppDailyActionSummary::actionsRewarded.name, Sort.Direction.ASC)
                        .on(AppDailyActionSummary::user.name, Sort.Direction.ASC),
            )
        )
    }
}
