package org.vechain.indexer.b3tr.challenges

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
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            IndexerNames.B3TR_USER_CHALLENGES.NAME,
            B3trUserChallenge::class.java,
            version,
        )
        ensureCollection()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(
                    B3trUserChallenge::wallet.name to Sort.Direction.ASC,
                    B3trUserChallenge::challengeCreatedAtBlockTimestamp.name to Sort.Direction.DESC,
                    B3trUserChallenge::challengeId.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    B3trUserChallenge::wallet.name to Sort.Direction.ASC,
                    B3trUserChallenge::challengeId.name to Sort.Direction.ASC,
                ),
                buildIndex(B3trUserChallenge::challengeId.name to Sort.Direction.ASC),
                buildIndex(
                    B3trUserChallenge::wallet.name to Sort.Direction.ASC,
                    B3trUserChallenge::participantStatus.name to Sort.Direction.ASC,
                    B3trUserChallenge::challengeCreatedAtBlockTimestamp.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    B3trUserChallenge::wallet.name to Sort.Direction.ASC,
                    B3trUserChallenge::isCreator.name to Sort.Direction.ASC,
                    B3trUserChallenge::challengeCreatedAtBlockTimestamp.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    B3trUserChallenge::wallet.name to Sort.Direction.ASC,
                    B3trUserChallenge::isWinner.name to Sort.Direction.ASC,
                    B3trUserChallenge::hasClaimedPrize.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
