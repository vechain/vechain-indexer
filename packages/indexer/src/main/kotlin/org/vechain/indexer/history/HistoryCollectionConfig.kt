package org.vechain.indexer.history

import kotlinx.coroutines.CoroutineScope
import org.bson.Document
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
                "tokenId_1_eventName_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::tokenId.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                "contractAddress_1_tokenId_1_eventName_1_blockTimestamp_-1" to
                    Index()
                        .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::tokenId.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC),
                "delegationId_1_blockNumber_-1_delegationLifecycleOrder_-1" to
                    Index()
                        .on(IndexedHistoryEvent::delegationId.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockNumber.name, Sort.Direction.DESC)
                        .on(
                            IndexedHistoryEvent.DELEGATION_LIFECYCLE_ORDER_FIELD,
                            Sort.Direction.DESC,
                        ),
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

        // Covering index for DelegationLifecycleHistoryService.ensureLoaded() aggregation.
        // The partial filter restricts the index to delegation documents only (~15K entries),
        // which satisfies the aggregation's match(delegationLifecycleStatus exists/ne null).
        // The key order matches the sort/group stages: group(delegationId) with
        // sort(delegationId asc, blockNumber desc, delegationLifecycleOrder desc).
        ensureIndexes(
            listOf(
                "dlc_delegationId_1_blockNumber_-1_dlcOrder_-1" to
                    Index()
                        .on(IndexedHistoryEvent::delegationId.name, Sort.Direction.ASC)
                        .on(IndexedHistoryEvent::blockNumber.name, Sort.Direction.DESC)
                        .on(
                            IndexedHistoryEvent.DELEGATION_LIFECYCLE_ORDER_FIELD,
                            Sort.Direction.DESC,
                        )
            ),
            partialFilter =
                Document(
                    IndexedHistoryEvent.DELEGATION_LIFECYCLE_STATUS_FIELD,
                    Document("\$exists", true),
                ),
        )
    }
}
