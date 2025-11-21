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
import org.vechain.indexer.b3tr.action.AppAllTimeActionSummary
import org.vechain.indexer.b3tr.action.AppAllTimeActionSummaryArchive
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-all-time-action-summary")
open class AppAllTimeActionSummaryCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    @param:Value("\${indexer.version.b3tr-app-all-time-action-summary}") private val version: Int,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        AppAllTimeActionSummary::class.java,
        AppAllTimeActionSummaryArchive::class.java,
    ) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                indexerName = IndexerNames.APP_ALL_TIME_ACTION_SUMMARY,
                AppAllTimeActionSummary::class.java,
                version,
            )

        if (dropped) {
            indexerVersionService.dropArchiveCollection(AppAllTimeActionSummaryArchive::class.java)
        }

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "user_-1" to Index().on(AppAllTimeActionSummary::user.name, Sort.Direction.DESC),
                "blockNumber_-1" to
                    Index().on(AppAllTimeActionSummary::blockNumber.name, Sort.Direction.DESC),
                "appId_1_totalRewardAmount_-1_user_1" to
                    Index()
                        .on(AppAllTimeActionSummary::appId.name, Sort.Direction.ASC)
                        .on(AppAllTimeActionSummary::totalRewardAmount.name, Sort.Direction.DESC)
                        .on(AppAllTimeActionSummary::user.name, Sort.Direction.ASC),
                "appId_1_totalRewardAmount_1_user_1" to
                    Index()
                        .on(AppAllTimeActionSummary::appId.name, Sort.Direction.ASC)
                        .on(AppAllTimeActionSummary::totalRewardAmount.name, Sort.Direction.ASC)
                        .on(AppAllTimeActionSummary::user.name, Sort.Direction.ASC),
                "appId_1_actionsRewarded_-1_user_1" to
                    Index()
                        .on(AppAllTimeActionSummary::appId.name, Sort.Direction.ASC)
                        .on(AppAllTimeActionSummary::actionsRewarded.name, Sort.Direction.DESC)
                        .on(AppAllTimeActionSummary::user.name, Sort.Direction.ASC),
                "appId_1_actionsRewarded_1_user_1" to
                    Index()
                        .on(AppAllTimeActionSummary::appId.name, Sort.Direction.ASC)
                        .on(AppAllTimeActionSummary::actionsRewarded.name, Sort.Direction.ASC)
                        .on(AppAllTimeActionSummary::user.name, Sort.Direction.ASC),
            )
        )
    }
}
