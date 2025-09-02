package org.vechain.indexer.historical.vote_tally

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("historical-proposals")
@Configuration
open class VoteTallyCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, VoteTally::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.historical_proposals}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            VoteTally::class.java,
            version,
        )
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                "proposalId_1" to Index().on("proposalId", Sort.Direction.ASC),
                "blockNumber_1" to Index().on("blockNumber", Sort.Direction.ASC),
            )
        )
    }
}
