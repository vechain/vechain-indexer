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
import org.vechain.indexer.b3tr.action.UserAllTimeActionSummary
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-all-time-action-summary")
open class UserAllTimeActionSummaryCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    @param:Value("\${indexer.version.b3tr-user-all-time-action-summary}") private val version: Int,
) : CollectionConfig(mongoTemplate, appCoroutineScope, UserAllTimeActionSummary::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.USER_ALL_TIME_ACTION_SUMMARY.NAME,
            UserAllTimeActionSummary::class.java,
            version,
        )
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(UserAllTimeActionSummary::entity.name to Sort.Direction.DESC),
                buildIndex(
                    UserAllTimeActionSummary::entityType.name to Sort.Direction.ASC,
                    UserAllTimeActionSummary::actionsRewarded.name to Sort.Direction.DESC,
                    UserAllTimeActionSummary::entity.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    UserAllTimeActionSummary::entityType.name to Sort.Direction.ASC,
                    UserAllTimeActionSummary::actionsRewarded.name to Sort.Direction.ASC,
                    UserAllTimeActionSummary::entity.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    UserAllTimeActionSummary::entityType.name to Sort.Direction.ASC,
                    UserAllTimeActionSummary::totalRewardAmount.name to Sort.Direction.DESC,
                    UserAllTimeActionSummary::entity.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    UserAllTimeActionSummary::entityType.name to Sort.Direction.ASC,
                    UserAllTimeActionSummary::totalRewardAmount.name to Sort.Direction.ASC,
                    UserAllTimeActionSummary::entity.name to Sort.Direction.ASC,
                ),
                buildIndex(UserAllTimeActionSummary::entityType.name to Sort.Direction.ASC),
                buildIndex("blockNumber" to Sort.Direction.ASC),
            )
        )
    }
}
