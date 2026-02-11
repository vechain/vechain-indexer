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
import org.vechain.indexer.b3tr.action.UserDailyActionSummary
import org.vechain.indexer.b3tr.action.UserDailyActionSummaryArchive
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-daily-action-summary")
open class UserDailyActionSummaryCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    @param:Value("\${indexer.version.b3tr-user-daily-action-summary}") private val version: Int,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        UserDailyActionSummary::class.java,
        UserDailyActionSummaryArchive::class.java,
    ) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                indexerName = IndexerNames.USER_DAILY_ACTION_SUMMARY.NAME,
                UserDailyActionSummary::class.java,
                version,
            )

        if (dropped) {
            indexerVersionService.dropArchiveCollection(UserDailyActionSummaryArchive::class.java)
        }

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "entity_-1" to Index().on(UserDailyActionSummary::entity.name, Sort.Direction.DESC),
                "date_-1" to Index().on(UserDailyActionSummary::date.name, Sort.Direction.DESC),
                "blockNumber_-1" to
                    Index().on(UserDailyActionSummary::blockNumber.name, Sort.Direction.DESC),
                "entityType_1_actionsRewarded_-1_entity_1" to
                    Index()
                        .on(UserDailyActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::actionsRewarded.name, Sort.Direction.DESC)
                        .on(UserDailyActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_actionsRewarded_1_entity_1" to
                    Index()
                        .on(UserDailyActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::actionsRewarded.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_totalRewardAmount_-1_entity_1" to
                    Index()
                        .on(UserDailyActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::totalRewardAmount.name, Sort.Direction.DESC)
                        .on(UserDailyActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_totalRewardAmount_1_entity_1" to
                    Index()
                        .on(UserDailyActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::totalRewardAmount.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_date_1_actionsRewarded_-1_entity_1" to
                    Index()
                        .on(UserDailyActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::date.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::actionsRewarded.name, Sort.Direction.DESC)
                        .on(UserDailyActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_date_1_actionsRewarded_1_entity_1" to
                    Index()
                        .on(UserDailyActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::date.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::actionsRewarded.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_date_1_totalRewardAmount_-1_entity_1" to
                    Index()
                        .on(UserDailyActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::date.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::totalRewardAmount.name, Sort.Direction.DESC)
                        .on(UserDailyActionSummary::entity.name, Sort.Direction.ASC),
                "entityType_1_date_1_totalRewardAmount_1_entity_1" to
                    Index()
                        .on(UserDailyActionSummary::entityType.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::date.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::totalRewardAmount.name, Sort.Direction.ASC)
                        .on(UserDailyActionSummary::entity.name, Sort.Direction.ASC),
            )
        )
    }
}
