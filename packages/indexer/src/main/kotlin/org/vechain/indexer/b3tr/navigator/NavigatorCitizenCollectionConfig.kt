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

@Profile("b3tr", "b3tr-navigator", "b3tr-navigator-citizen")
@Configuration
open class NavigatorCitizenCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, NavigatorCitizen::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.b3tr-navigator}") private val version: Int = 1

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.NAVIGATOR_CITIZEN.NAME,
            NavigatorCitizen::class.java,
            version,
        )
        ensureCollection()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(NavigatorCitizen::blockNumber.name to Sort.Direction.DESC),
                buildIndex(
                    NavigatorCitizen::navigator.name to Sort.Direction.ASC,
                    NavigatorCitizen::active.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    NavigatorCitizen::navigator.name to Sort.Direction.ASC,
                    NavigatorCitizen::active.name to Sort.Direction.ASC,
                    NavigatorCitizen::delegatedAt.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                ),
                buildIndex(
                    NavigatorCitizen::active.name to Sort.Direction.ASC,
                    NavigatorCitizen::navigatorExitEffectiveDeadlineBlock.name to
                        Sort.Direction.ASC,
                ),
            )
        )
    }
}
