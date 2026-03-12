package org.vechain.indexer.b3tr.gm

import kotlin.jvm.java
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

@Profile("b3tr", "b3tr-gm-nft")
@Configuration
open class GmNftCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, GmNft::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.b3tr-gm-nft}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.GM_NFT.NAME,
            GmNft::class.java,
            version,
        )
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                "attachedNodeId_1" to Index().on(GmNft::attachedNodeId.name, Sort.Direction.ASC),
                "blockNumber_1" to Index().on("blockNumber", Sort.Direction.ASC),
                "blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
                "level_1_owner_1" to
                    Index()
                        .on(GmNft::level.name, Sort.Direction.ASC)
                        .on(GmNft::owner.name, Sort.Direction.ASC),
            )
        )
    }
}
