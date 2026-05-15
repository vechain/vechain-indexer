package org.vechain.indexer.validator

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

@Profile(
    "validator",
    "delegation",
    "stargate",
    "stargate-token",
    "token-reward",
    "validator-reward",
    "history",
)
@Configuration
open class ValidatorCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, Validator::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.validator}") private val version: Int = 1

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            IndexerNames.VALIDATOR.NAME,
            Validator::class.java,
            version,
        )
        this.ensureCollection()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(Validator::status.name to Sort.Direction.ASC),
                buildIndex(Validator::endorser.name to Sort.Direction.ASC),
                buildIndex(Validator::lastMissedBlockNumber.name to Sort.Direction.ASC),
                buildIndex(Validator::lastProposedBlockNumber.name to Sort.Direction.ASC),
            )
        )
    }
}
