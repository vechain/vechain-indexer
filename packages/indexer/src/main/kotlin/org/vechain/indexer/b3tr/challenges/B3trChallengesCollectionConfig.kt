package org.vechain.indexer.b3tr.challenges

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("b3tr", "b3tr-challenges")
@Configuration
open class B3trChallengesCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, B3trChallenge::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.b3tr-challenges}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            IndexerNames.B3TR_CHALLENGES.NAME,
            B3trChallenge::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(
                    B3trChallenge::createdAtBlockTimestamp.name to Sort.Direction.DESC,
                    B3trChallenge::challengeId.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    B3trChallenge::visibility.name to Sort.Direction.ASC,
                    B3trChallenge::status.name to Sort.Direction.ASC,
                    B3trChallenge::createdAtBlockTimestamp.name to Sort.Direction.DESC,
                    B3trChallenge::challengeId.name to Sort.Direction.DESC,
                ),
                buildIndex(B3trChallenge::creator.name to Sort.Direction.ASC),
                buildIndex(B3trChallenge::status.name to Sort.Direction.ASC),
                buildIndex(B3trChallenge::kind.name to Sort.Direction.ASC),
                buildIndex(B3trChallenge::visibility.name to Sort.Direction.ASC),
                buildIndex(B3trChallenge::startRound.name to Sort.Direction.DESC),
                buildIndex(B3trChallenge::endRound.name to Sort.Direction.DESC),
                buildIndex(B3trChallenge::participants.name to Sort.Direction.ASC),
                buildIndex(B3trChallenge::invited.name to Sort.Direction.ASC),
                buildIndex(B3trChallenge::declined.name to Sort.Direction.ASC),
                buildIndex(B3trChallenge::selectedApps.name to Sort.Direction.ASC),
            )
        )
    }
}
