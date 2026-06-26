package org.vechain.indexer.history

import kotlinx.coroutines.CoroutineScope
import org.bson.Document
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
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.HISTORY.NAME,
            IndexedHistoryEvent::class.java,
            version,
        )
        this.ensureCollection()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                // Collapses the 5-way $or over origin/gasPayer/to/from/owner in the API
                // account-history query into a single multikey equality. Keyed for the
                // blockTimestamp DESC sort and the eventName $in filter that ride along.
                buildIndex(
                    IndexedHistoryEvent.INVOLVED_ADDRESSES_FIELD to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockTimestamp.name to Sort.Direction.DESC,
                    IndexedHistoryEvent::eventName.name to Sort.Direction.ASC,
                ),
                // Per-address indexes for the GET /history?searchBy=... path, which builds a
                // $or over caller-specified fields (ValidSearchBy: to, from, origin, gasPayer)
                // and CAN'T use involvedAddresses (semantics differ — searchBy lets callers
                // restrict to a single source field). `to` is covered by the wider
                // to+eventName+blockTimestamp index below; `owner` isn't in ValidSearchBy.
                buildIndex(
                    IndexedHistoryEvent::origin.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockTimestamp.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedHistoryEvent::from.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockTimestamp.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedHistoryEvent::gasPayer.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockTimestamp.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedHistoryEvent::tokenId.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockTimestamp.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedHistoryEvent::tokenId.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::eventName.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockTimestamp.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedHistoryEvent::contractAddress.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::tokenId.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::eventName.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockTimestamp.name to Sort.Direction.DESC,
                ),
                // Action endpoints query narrower shapes and benefit from dedicated compounds.
                buildIndex(
                    IndexedHistoryEvent::to.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::eventName.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockTimestamp.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedHistoryEvent::to.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::appId.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::eventName.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockTimestamp.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedHistoryEvent::appId.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::eventName.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockTimestamp.name to Sort.Direction.DESC,
                ),
            )
        )

        // Covering index for DelegationLifecycleHistoryService.ensureLoaded() aggregation.
        // The partial filter restricts the index to delegation documents only (~15K entries),
        // which satisfies the aggregation's match(delegationLifecycleStatus exists/ne null).
        // The key order matches the sort/group stages: group(delegationId) with
        // sort(delegationId asc, blockNumber desc, delegationLifecycleOrder desc).
        ensureIndexes(
            listOf(
                buildIndex(
                    IndexedHistoryEvent::delegationId.name to Sort.Direction.ASC,
                    IndexedHistoryEvent::blockNumber.name to Sort.Direction.DESC,
                    IndexedHistoryEvent.DELEGATION_LIFECYCLE_ORDER_FIELD to Sort.Direction.DESC,
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
