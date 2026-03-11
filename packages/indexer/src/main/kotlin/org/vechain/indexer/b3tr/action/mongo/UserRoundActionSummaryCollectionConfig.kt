package org.vechain.indexer.b3tr.action.mongo

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.action.UserRoundActionSummary
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    @param:Value("\${indexer.version.b3tr-user-round-action-summary}") private val version: Int,
) : CollectionConfig(mongoTemplate, appCoroutineScope, UserRoundActionSummary::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.USER_ROUND_ACTION_SUMMARY.NAME,
            UserRoundActionSummary::class.java,
            version,
        )
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                "entity_-1" to Index().on(UserRoundActionSummary::entity.name, Sort.Direction.DESC),
                "entity_1_roundId_1" to
                    Index()
                        .on(UserRoundActionSummary::entity.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::roundId.name, Sort.Direction.ASC),
                "entityType_1_roundId_1" to
                    Index()
                        .on(UserRoundActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::roundId.name, Sort.Direction.ASC),
                "entityType_1_roundId_1_actionsRewarded_-1_entity_1" to
                    Index()
                        .on(UserRoundActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::roundId.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::actionsRewarded.name, Sort.Direction.DESC)
                        .on(UserRoundActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_roundId_1_actionsRewarded_1_entity_1" to
                    Index()
                        .on(UserRoundActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::roundId.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::actionsRewarded.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_roundId_1_totalRewardAmount_-1_entity_1" to
                    Index()
                        .on(UserRoundActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::roundId.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::totalRewardAmount.name, Sort.Direction.DESC)
                        .on(UserRoundActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_roundId_1_totalRewardAmount_1_entity_1" to
                    Index()
                        .on(UserRoundActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::roundId.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::totalRewardAmount.name, Sort.Direction.ASC)
                        .on(UserRoundActionSummary::entity.name, Sort.Direction.ASC),
                "blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
            )
        )
    }
}
