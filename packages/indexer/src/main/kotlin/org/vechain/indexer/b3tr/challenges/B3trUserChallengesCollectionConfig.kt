package org.vechain.indexer.b3tr.challenges

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("b3tr", "b3tr-challenges")
@Configuration
open class B3trUserChallengesCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, B3trUserChallenge::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.b3tr-user-challenges}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            IndexerNames.B3TR_USER_CHALLENGES.NAME,
            B3trUserChallenge::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                "wallet_1_isRelevant_1_challengeCreatedAtBlockTimestamp_-1_challengeId_-1" to
                    Index()
                        .on(B3trUserChallenge::wallet.name, Sort.Direction.ASC)
                        .on(B3trUserChallenge::isRelevant.name, Sort.Direction.ASC)
                        .on(
                            B3trUserChallenge::challengeCreatedAtBlockTimestamp.name,
                            Sort.Direction.DESC,
                        )
                        .on(B3trUserChallenge::challengeId.name, Sort.Direction.DESC),
                "wallet_1_isActionable_1_challengeCreatedAtBlockTimestamp_-1_challengeId_-1" to
                    Index()
                        .on(B3trUserChallenge::wallet.name, Sort.Direction.ASC)
                        .on(B3trUserChallenge::isActionable.name, Sort.Direction.ASC)
                        .on(
                            B3trUserChallenge::challengeCreatedAtBlockTimestamp.name,
                            Sort.Direction.DESC,
                        )
                        .on(B3trUserChallenge::challengeId.name, Sort.Direction.DESC),
                "wallet_1_isParticipating_1_challengeCreatedAtBlockTimestamp_-1_challengeId_-1" to
                    Index()
                        .on(B3trUserChallenge::wallet.name, Sort.Direction.ASC)
                        .on(B3trUserChallenge::isParticipating.name, Sort.Direction.ASC)
                        .on(
                            B3trUserChallenge::challengeCreatedAtBlockTimestamp.name,
                            Sort.Direction.DESC,
                        )
                        .on(B3trUserChallenge::challengeId.name, Sort.Direction.DESC),
                "wallet_1_isHistorical_1_challengeCreatedAtBlockTimestamp_-1_challengeId_-1" to
                    Index()
                        .on(B3trUserChallenge::wallet.name, Sort.Direction.ASC)
                        .on(B3trUserChallenge::isHistorical.name, Sort.Direction.ASC)
                        .on(
                            B3trUserChallenge::challengeCreatedAtBlockTimestamp.name,
                            Sort.Direction.DESC,
                        )
                        .on(B3trUserChallenge::challengeId.name, Sort.Direction.DESC),
                "wallet_1_challengeId_1" to
                    Index()
                        .on(B3trUserChallenge::wallet.name, Sort.Direction.ASC)
                        .on(B3trUserChallenge::challengeId.name, Sort.Direction.ASC),
                "challengeId_1" to
                    Index().on(B3trUserChallenge::challengeId.name, Sort.Direction.ASC),
            )
        )
    }
}
