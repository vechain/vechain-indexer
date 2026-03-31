package org.vechain.indexer.b3tr.navigator

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

@Profile("b3tr", "b3tr-navigator")
@Configuration
open class NavigatorDelegationCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, NavigatorDelegation::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.b3tr-navigator}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.NAVIGATOR_DELEGATION.NAME,
            NavigatorDelegation::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                "blockNumber_-1" to
                    Index().on(NavigatorDelegation::blockNumber.name, Sort.Direction.DESC),
                "navigator_delegation_citizen_1_blockTimestamp_-1" to
                    Index()
                        .on(NavigatorDelegation::citizen.name, Sort.Direction.ASC)
                        .on(NavigatorDelegation::blockTimestamp.name, Sort.Direction.DESC),
                "navigator_delegation_navigator_1_blockTimestamp_-1" to
                    Index()
                        .on(NavigatorDelegation::navigator.name, Sort.Direction.ASC)
                        .on(NavigatorDelegation::blockTimestamp.name, Sort.Direction.DESC),
                "navigator_delegation_roundId_1_blockTimestamp_-1" to
                    Index()
                        .on(NavigatorDelegation::roundId.name, Sort.Direction.ASC)
                        .on(NavigatorDelegation::blockTimestamp.name, Sort.Direction.DESC),
            )
        )
    }
}
