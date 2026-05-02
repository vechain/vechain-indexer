package org.vechain.indexer.stargate.token

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

@Profile("stargate", "stargate-token")
@Configuration
open class StargateTokenCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, StargateToken::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.stargate-token}") private var version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.STARGATE_TOKEN.NAME,
            StargateToken::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(StargateToken::manager.name to Sort.Direction.ASC),
                buildIndex(
                    StargateToken::owner.name to Sort.Direction.ASC,
                    StargateToken::manager.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    StargateToken::delegationNextPeriod.name to Sort.Direction.ASC,
                    StargateToken::delegationStatus.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    StargateToken::validatorId.name to Sort.Direction.ASC,
                    StargateToken::blockNumber.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
