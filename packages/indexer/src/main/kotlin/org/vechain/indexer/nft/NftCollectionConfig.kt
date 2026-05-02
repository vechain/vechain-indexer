package org.vechain.indexer.nft

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("nfts")
@Configuration
open class NftCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, IndexedNft::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.nfts}") private var version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.NFT.NAME,
            IndexedNft::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildUniqueIndex(
                    IndexedNft::contractAddress.name to Sort.Direction.ASC,
                    IndexedNft::tokenId.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedNft::owner.name to Sort.Direction.ASC,
                    IndexedNft::blockNumber.name to Sort.Direction.DESC,
                    IndexedNft::txId.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                    IndexedNft::isBlacklisted.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    IndexedNft::contractAddress.name to Sort.Direction.ASC,
                    IndexedNft::blockNumber.name to Sort.Direction.DESC,
                    IndexedNft::txId.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                    IndexedNft::isBlacklisted.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    IndexedNft::owner.name to Sort.Direction.ASC,
                    IndexedNft::contractAddress.name to Sort.Direction.ASC,
                    IndexedNft::blockNumber.name to Sort.Direction.DESC,
                    IndexedNft::txId.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                    IndexedNft::isBlacklisted.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    IndexedNft::owner.name to Sort.Direction.ASC,
                    IndexedNft::contractAddress.name to Sort.Direction.ASC,
                    IndexedNft::tokenId.name to Sort.Direction.ASC,
                    IndexedNft::blockNumber.name to Sort.Direction.DESC,
                    IndexedNft::txId.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                    IndexedNft::isBlacklisted.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
