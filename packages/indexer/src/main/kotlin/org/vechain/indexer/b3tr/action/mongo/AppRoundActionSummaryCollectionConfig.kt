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
import org.vechain.indexer.b3tr.action.AppRoundActionSummary
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
open class AppRoundActionSummaryCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    @param:Value("\${indexer.version.b3tr-app-round-action-summary}") private val version: Int,
) : CollectionConfig(mongoTemplate, appCoroutineScope, AppRoundActionSummary::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.APP_ROUND_ACTION_SUMMARY.NAME,
            AppRoundActionSummary::class.java,
            version,
        )
        this.ensureCollection()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(
                    AppRoundActionSummary::appId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::roundId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::user.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AppRoundActionSummary::appId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::roundId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::totalRewardAmount.name to Sort.Direction.DESC,
                    AppRoundActionSummary::user.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AppRoundActionSummary::appId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::roundId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::totalRewardAmount.name to Sort.Direction.ASC,
                    AppRoundActionSummary::user.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AppRoundActionSummary::appId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::roundId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::actionsRewarded.name to Sort.Direction.DESC,
                    AppRoundActionSummary::user.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AppRoundActionSummary::appId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::roundId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::actionsRewarded.name to Sort.Direction.ASC,
                    AppRoundActionSummary::user.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AppRoundActionSummary::roundId.name to Sort.Direction.ASC,
                    AppRoundActionSummary::user.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
