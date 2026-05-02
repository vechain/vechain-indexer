package org.vechain.indexer.stargate.rewards

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.version.IndexerVersionService

@Profile("token-reward")
@Configuration
open class TokenRewardCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, TokenReward::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.token-rewards}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.TOKEN_REWARD.NAME,
            TokenReward::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        // Ensure indexes
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(
                    TokenReward::validator.name to Sort.Direction.ASC,
                    TokenReward::rewardPeriod.name to Sort.Direction.ASC,
                    TokenReward::cycle.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    TokenReward::tokenId.name to Sort.Direction.ASC,
                    TokenReward::rewardPeriod.name to Sort.Direction.ASC,
                    TokenReward::validator.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
