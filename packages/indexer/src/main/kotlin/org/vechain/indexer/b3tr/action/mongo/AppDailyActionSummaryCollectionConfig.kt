package org.vechain.indexer.b3tr.action.mongo

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexedDocument
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
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(
                    AppDailyActionSummary::user.name to Sort.Direction.DESC,
                    AppDailyActionSummary::date.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    AppDailyActionSummary::appId.name to Sort.Direction.ASC,
                    AppDailyActionSummary::date.name to Sort.Direction.ASC,
                    AppDailyActionSummary::user.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AppDailyActionSummary::appId.name to Sort.Direction.ASC,
                    AppDailyActionSummary::date.name to Sort.Direction.ASC,
                    AppDailyActionSummary::totalRewardAmount.name to Sort.Direction.DESC,
                    AppDailyActionSummary::user.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AppDailyActionSummary::appId.name to Sort.Direction.ASC,
                    AppDailyActionSummary::date.name to Sort.Direction.ASC,
                    AppDailyActionSummary::totalRewardAmount.name to Sort.Direction.ASC,
                    AppDailyActionSummary::user.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AppDailyActionSummary::appId.name to Sort.Direction.ASC,
                    AppDailyActionSummary::date.name to Sort.Direction.ASC,
                    AppDailyActionSummary::actionsRewarded.name to Sort.Direction.DESC,
                    AppDailyActionSummary::user.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AppDailyActionSummary::appId.name to Sort.Direction.ASC,
                    AppDailyActionSummary::date.name to Sort.Direction.ASC,
                    AppDailyActionSummary::actionsRewarded.name to Sort.Direction.ASC,
                    AppDailyActionSummary::user.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
