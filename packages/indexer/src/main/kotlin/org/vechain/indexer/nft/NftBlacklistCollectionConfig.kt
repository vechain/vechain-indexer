package org.vechain.indexer.nft

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
import org.vechain.indexer.version.IndexerVersionService

@Profile("nfts", "history")
@Configuration
open class NftBlacklistCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, NftBlacklist::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.nft-blacklist}") private val version: Int = 1

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.NFT_BLACKLIST.NAME,
            NftBlacklist::class.java,
            version,
        )
        ensureCollection()
        // Reads probe by `_id`; blockNumber only serves getLatestRecord and rollback.
        ensureIndexes(listOf(buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC)))
    }
}
