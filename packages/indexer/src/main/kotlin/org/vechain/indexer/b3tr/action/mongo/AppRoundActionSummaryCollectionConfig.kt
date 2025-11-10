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
import org.vechain.indexer.b3tr.action.AppRoundActionSummary
import org.vechain.indexer.b3tr.action.AppRoundActionSummaryArchive
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
open class AppRoundActionSummaryCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    @param:Value("\${indexer.version.b3tr-app-round-action-summary}") private val version: Int,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        AppRoundActionSummary::class.java,
        AppRoundActionSummaryArchive::class.java,
    ) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                indexerName = IndexerNames.APP_ROUND_ACTION_SUMMARY,
                AppRoundActionSummary::class.java,
                version,
            )

        if (dropped) {
            indexerVersionService.dropArchiveCollection(AppRoundActionSummaryArchive::class.java)
        }

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
                "appId_-1_user_-1" to
                    Index().on("appId", Sort.Direction.DESC).on("user", Sort.Direction.DESC),
                "appId_1_roundId_1_totalRewardAmount_-1_user_1" to
                    Index()
                        .on("appId", Sort.Direction.ASC)
                        .on("roundId", Sort.Direction.ASC)
                        .on("totalRewardAmount", Sort.Direction.DESC)
                        .on("user", Sort.Direction.ASC),
                "appId_1_roundId_1_totalRewardAmount_1_user_1" to
                    Index()
                        .on("appId", Sort.Direction.ASC)
                        .on("roundId", Sort.Direction.ASC)
                        .on("totalRewardAmount", Sort.Direction.ASC)
                        .on("user", Sort.Direction.ASC),
                "appId_1_roundId_1_actionsRewarded_-1_user_1" to
                    Index()
                        .on("appId", Sort.Direction.ASC)
                        .on("roundId", Sort.Direction.ASC)
                        .on("actionsRewarded", Sort.Direction.DESC)
                        .on("user", Sort.Direction.ASC),
                "appId_1_roundId_1_actionsRewarded_1_user_1" to
                    Index()
                        .on("appId", Sort.Direction.ASC)
                        .on("roundId", Sort.Direction.ASC)
                        .on("actionsRewarded", Sort.Direction.ASC)
                        .on("user", Sort.Direction.ASC),
                "roundId_1_user_1" to
                    Index().on("roundId", Sort.Direction.ASC).on("user", Sort.Direction.ASC),
            )
        )
    }
}
