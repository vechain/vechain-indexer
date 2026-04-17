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
open class NavigatorOverviewSummaryCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, NavigatorOverviewSummary::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.b3tr-navigator}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.NAVIGATOR_OVERVIEW_SUMMARY.NAME,
            NavigatorOverviewSummary::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                "recordType_1_status_1_exitEffectiveDeadlineBlock_1" to
                    Index()
                        .on(NavigatorOverviewSummary::recordType.name, Sort.Direction.ASC)
                        .on(NavigatorOverviewSummary::status.name, Sort.Direction.ASC)
                        .on(
                            NavigatorOverviewSummary::exitEffectiveDeadlineBlock.name,
                            Sort.Direction.ASC,
                        )
            ),
            partialFilter = null,
        )
    }
}
