package org.vechain.indexer.b3tr.challenges

import kotlin.jvm.java
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
                "createdAtBlockTimestamp_-1_challengeId_-1" to
                    Index()
                        .on(B3trChallenge::createdAtBlockTimestamp.name, Sort.Direction.DESC)
                        .on(B3trChallenge::challengeId.name, Sort.Direction.DESC),
                "creator_1" to Index().on(B3trChallenge::creator.name, Sort.Direction.ASC),
                "status_1" to Index().on(B3trChallenge::status.name, Sort.Direction.ASC),
                "kind_1" to Index().on(B3trChallenge::kind.name, Sort.Direction.ASC),
                "visibility_1" to Index().on(B3trChallenge::visibility.name, Sort.Direction.ASC),
                "startRound_-1" to Index().on(B3trChallenge::startRound.name, Sort.Direction.DESC),
                "endRound_-1" to Index().on(B3trChallenge::endRound.name, Sort.Direction.DESC),
                "participants_1" to
                    Index().on(B3trChallenge::participants.name, Sort.Direction.ASC),
                "invited_1" to Index().on(B3trChallenge::invited.name, Sort.Direction.ASC),
                "declined_1" to Index().on(B3trChallenge::declined.name, Sort.Direction.ASC),
                "selectedApps_1" to
                    Index().on(B3trChallenge::selectedApps.name, Sort.Direction.ASC),
                "blockNumber_-1" to Index().on(B3trChallenge::blockNumber.name, Sort.Direction.DESC),
            )
        )
    }
}
