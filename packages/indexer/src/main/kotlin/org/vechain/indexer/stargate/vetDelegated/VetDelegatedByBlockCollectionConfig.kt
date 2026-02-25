package org.vechain.indexer.stargate.vetDelegated

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
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

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.VET_DELEGATED_BY_BLOCK.NAME,
            VetDelegatedByBlock::class.java,
            version,
        )

        ensureCollection()

        // Ensure indexes
        ensureIndexes(
            listOf(
                "blockNumber_1_unique" to
                    Index().on(IndexedDocument::blockNumber.name, Sort.Direction.ASC).unique(),
                "blockNumber_-1" to
                    Index().on(IndexedDocument::blockNumber.name, Sort.Direction.DESC),
                "blockTimestamp_1" to
                    Index().on(IndexedDocument::blockTimestamp.name, Sort.Direction.ASC),
                "timeFrames_1_blockTimestamp_1" to
                    Index()
                        .on(TimeFrameDocument::timeFrames.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockTimestamp.name, Sort.Direction.ASC),
            )
        )
    }
}
