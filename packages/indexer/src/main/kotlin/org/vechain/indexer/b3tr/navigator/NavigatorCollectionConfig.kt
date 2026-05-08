package org.vechain.indexer.b3tr.navigator

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

@Profile("b3tr", "b3tr-navigator", "b3tr-navigator-main")
@Configuration
open class NavigatorCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, Navigator::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.b3tr-navigator}") private val version: Int = 1

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.NAVIGATOR.NAME,
            Navigator::class.java,
            version,
        )
        ensureCollection()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(Navigator::blockNumber.name to Sort.Direction.DESC),
                buildIndex(Navigator::status.name to Sort.Direction.ASC),
                buildIndex(
                    Navigator::status.name to Sort.Direction.ASC,
                    Navigator::exitEffectiveDeadlineBlock.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    Navigator::status.name to Sort.Direction.ASC,
                    Navigator::blockTimestamp.name to Sort.Direction.DESC,
                ),
                buildIndex(Navigator::stake.name to Sort.Direction.DESC),
                buildIndex(Navigator::totalDelegated.name to Sort.Direction.DESC),
                buildIndex(Navigator::citizenCount.name to Sort.Direction.DESC),
                buildIndex(Navigator::registeredAt.name to Sort.Direction.DESC),
            )
        )
    }
}
