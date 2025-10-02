package org.vechain.indexer.stargate

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
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "vtho-claimed-by-block")
@Configuration
open class VthoClaimedByBlockCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, VthoClaimedByBlock::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.stargate-vtho-claimed-by-block}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = "StargateVthoClaimedByBlockIndexer",
            VthoClaimedByBlock::class.java,
            version,
        )

        ensureCollection()

        // Ensure indexes
        ensureIndexes(
            listOf(
                "blockTimestamp_1" to
                    Index().on(IndexedDocument::blockTimestamp.name, Sort.Direction.ASC)
            )
        )
    }
}
