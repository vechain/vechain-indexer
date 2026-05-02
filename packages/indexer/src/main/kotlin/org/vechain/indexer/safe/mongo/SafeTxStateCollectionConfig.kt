package org.vechain.indexer.safe.mongo

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.safe.SafeTxState
import org.vechain.indexer.version.IndexerVersionService

@Profile("safe")
@Configuration
open class SafeTxStateCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, SafeTxState::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.safe-tx-state:1}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.SAFE_TX_STATE.NAME,
            SafeTxState::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                // Supports point lookup by (safe, txHash) and the batch endpoint that filters by
                // safe and the supplied set of txHashes.
                buildIndex(
                    SafeTxState::safe.name to Sort.Direction.ASC,
                    SafeTxState::txHash.name to Sort.Direction.ASC,
                )
            )
        )
    }
}
