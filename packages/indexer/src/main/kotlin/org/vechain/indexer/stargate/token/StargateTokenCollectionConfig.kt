package org.vechain.indexer.stargate.token

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
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "stargate-token")
@Configuration
open class StargateTokenCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        StargateToken::class.java,
        StargateTokenArchive::class.java,
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.stargate-token}") private var version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                indexerName = IndexerNames.STARGATE_TOKEN,
                StargateToken::class.java,
                version,
            )

        if (dropped) indexerVersionService.dropArchiveCollection(StargateTokenArchive::class.java)

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
                "manager_1" to Index().on("manager", Sort.Direction.ASC),
                "owner_1_manager_1" to
                    Index().on("owner", Sort.Direction.ASC).on("manager", Sort.Direction.ASC),
            )
        )
    }
}
