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

@Profile("validator", "validator-stats", "delegation")
@Configuration
open class DelegationCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, Delegation::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.delegation}") private val version: Int = 1

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            IndexerNames.DELEGATION.NAME,
            Delegation::class.java,
            version,
        )
        this.ensureCollection()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(
                    Delegation::validator.name to Sort.Direction.ASC,
                    Delegation::status.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    Delegation::status.name to Sort.Direction.ASC,
                    Delegation::validatorNextCycle.name to Sort.Direction.ASC,
                ),
                buildIndex(Delegation::tokenId.name to Sort.Direction.ASC),
                buildIndex(
                    Delegation::status.name to Sort.Direction.ASC,
                    Delegation::tokenLevel.name to Sort.Direction.ASC,
                    Delegation::stakedAmount.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    Delegation::status.name to Sort.Direction.ASC,
                    Delegation::validator.name to Sort.Direction.ASC,
                    Delegation::tokenLevel.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
