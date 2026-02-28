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

    @PostConstruct
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
                "entity_-1" to
                    Index().on(UserAllTimeActionSummary::entity.name, Sort.Direction.DESC),
                "entityType_1_actionsRewarded_-1_entity_1" to
                    Index()
                        .on(UserAllTimeActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserAllTimeActionSummary::actionsRewarded.name, Sort.Direction.DESC)
                        .on(UserAllTimeActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_actionsRewarded_1_entity_1" to
                    Index()
                        .on(UserAllTimeActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserAllTimeActionSummary::actionsRewarded.name, Sort.Direction.ASC)
                        .on(UserAllTimeActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_totalRewardAmount_-1_entity_1" to
                    Index()
                        .on(UserAllTimeActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserAllTimeActionSummary::totalRewardAmount.name, Sort.Direction.DESC)
                        .on(UserAllTimeActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_totalRewardAmount_1_entity_1" to
                    Index()
                        .on(UserAllTimeActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserAllTimeActionSummary::totalRewardAmount.name, Sort.Direction.ASC)
                        .on(UserAllTimeActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1" to
                    Index().on(UserAllTimeActionSummary::entityType.name, Sort.Direction.ASC),
                "blockNumber_1" to
                    Index().on(UserAllTimeActionSummary::blockNumber.name, Sort.Direction.ASC),
            )
        )
    }
}
