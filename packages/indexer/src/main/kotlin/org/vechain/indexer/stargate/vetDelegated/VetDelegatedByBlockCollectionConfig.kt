package org.vechain.indexer.stargate.vetDelegated

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
import org.vechain.indexer.stargate.timeFrame.TimeFrameDocument
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "vet-delegated-by-block")
@Configuration
open class VetDelegatedByBlockCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, VetDelegatedByBlock::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.stargate-vet-delegated-by-block}") private val version: Int = 1

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.VET_DELEGATED_BY_BLOCK.NAME,
            VetDelegatedByBlock::class.java,
            version,
        )
        ensureCollection()
        // Ensure indexes
        ensureIndexes(
            listOf(
                buildIndex(
                    "blockNumber" to Sort.Direction.DESC,
                    "txId" to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                ),
                buildUniqueIndex(IndexedDocument::blockNumber.name to Sort.Direction.ASC),
                buildIndex(IndexedDocument::blockTimestamp.name to Sort.Direction.ASC),
                buildIndex(
                    TimeFrameDocument::timeFrames.name to Sort.Direction.ASC,
                    IndexedDocument::blockTimestamp.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
