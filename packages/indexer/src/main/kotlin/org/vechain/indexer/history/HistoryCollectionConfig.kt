package org.vechain.indexer.history

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

@Profile("history")
@Configuration
open class HistoryCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
    appCoroutineScope: CoroutineScope,
) : CollectionConfig(mongoTemplate, appCoroutineScope, IndexedHistoryEvent::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.history}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.HISTORY.NAME,
            IndexedHistoryEvent::class.java,
            version,
        )
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                // For getLatestRecord() and deleteAllByBlockNumberGreaterThanEqual()
                "blockNumber_-1" to
                    Index().on(IndexedHistoryEvent::blockNumber.name, Sort.Direction.DESC),
                // Core account history queries fan out across these fields with blockTimestamp
                // sort.
                "origin_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::origin.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                "from_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::from.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                "to_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                "gasPayer_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::gasPayer.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                "owner_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::owner.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                "tokenId_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::tokenId.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                // Action endpoints query narrower shapes and benefit from dedicated compounds.
                "to_1_eventName_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                "to_1_appId_1_eventName_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::appId.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                "appId_1_eventName_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::appId.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
            )
        )
    }
}
