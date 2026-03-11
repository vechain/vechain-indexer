package org.vechain.indexer.validator

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

@Profile("validator", "delegation")
@Configuration
open class DelegationCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, Delegation::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.delegation}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            IndexerNames.DELEGATION.NAME,
            Delegation::class.java,
            version,
        )
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                "validator_1_status_1" to
                    Index()
                        .on(Delegation::validator.name, Sort.Direction.ASC)
                        .on(Delegation::status.name, Sort.Direction.ASC),
                "status_1_validatorNextCycle_1" to
                    Index()
                        .on(Delegation::status.name, Sort.Direction.ASC)
                        .on(Delegation::validatorNextCycle.name, Sort.Direction.ASC),
                "tokenId_1" to Index().on(Delegation::tokenId.name, Sort.Direction.ASC),
                "status_1_tokenLevel_1_stakedAmount_1" to
                    Index()
                        .on(Delegation::status.name, Sort.Direction.ASC)
                        .on(Delegation::tokenLevel.name, Sort.Direction.ASC)
                        .on(Delegation::stakedAmount.name, Sort.Direction.ASC),
                "notify_1_blockNumber_1" to
                    Index()
                        .on(Delegation::notify.name, Sort.Direction.ASC)
                        .on(Delegation::blockNumber.name, Sort.Direction.ASC),
            )
        )
    }
}
