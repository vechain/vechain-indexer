package org.vechain.indexer.stargate.rewards

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardArchive
import org.vechain.indexer.version.IndexerVersionService

@Profile("token-reward")
@Configuration
open class TokenRewardCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        TokenReward::class.java,
        TokenRewardArchive::class.java,
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.token-rewards}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                indexerName = IndexerNames.TOKEN_REWARD.NAME,
                TokenReward::class.java,
                version,
            )

        if (dropped) indexerVersionService.dropArchiveCollection(TokenRewardArchive::class.java)

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        // Ensure indexes
        ensureIndexes(
            listOf(
                // For getLatestRecord() and deleteAllByBlockNumberGreaterThanEqual()
                "blockNumber_-1" to
                    Index().on(IndexedDocument::blockNumber.name, Sort.Direction.DESC),
                // For global queries (all validators, sorted by timestamp)
                "blockTimestamp_-1" to
                    Index().on(IndexedDocument::blockTimestamp.name, Sort.Direction.DESC),
                "validator_1_rewardPeriod_1_cycle_-1" to
                    Index()
                        .on(TokenReward::validator.name, Sort.Direction.ASC)
                        .on(TokenReward::rewardPeriod.name, Sort.Direction.ASC)
                        .on(TokenReward::cycle.name, Sort.Direction.DESC),
                "tokenId_1_rewardPeriod_1_validator_1" to
                    Index()
                        .on(TokenReward::tokenId.name, Sort.Direction.ASC)
                        .on(TokenReward::rewardPeriod.name, Sort.Direction.ASC)
                        .on(TokenReward::validator.name, Sort.Direction.ASC),
            )
        )
    }
}
