package org.vechain.indexer.nft

import jakarta.annotation.PostConstruct
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

@Profile("nfts")
@Configuration
open class NftCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, IndexedNft::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.nfts}") private var version: Int = 1

    @PostConstruct
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
                "nft_contractAddress_1_tokenId_1" to
                    Index()
                        .on(IndexedNft::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedNft::tokenId.name, Sort.Direction.DESC)
                        .unique(),
                "nft_owner_1_blockNumber_-1_txId_-1__id_-1_isBlacklisted_1" to
                    Index()
                        .on(IndexedNft::owner.name, Sort.Direction.ASC)
                        .on(IndexedNft::blockNumber.name, Sort.Direction.DESC)
                        .on(IndexedNft::txId.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "nft_contractAddress_1_blockNumber_-1_txId_-1__id_-1_isBlacklisted_1" to
                    Index()
                        .on(IndexedNft::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedNft::blockNumber.name, Sort.Direction.DESC)
                        .on(IndexedNft::txId.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "nft_owner_1_contractAddress_1_blockNumber_-1_txId_-1__id_-1_isBlacklisted_1" to
                    Index()
                        .on(IndexedNft::owner.name, Sort.Direction.ASC)
                        .on(IndexedNft::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedNft::blockNumber.name, Sort.Direction.DESC)
                        .on(IndexedNft::txId.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
                "nft_owner_1_contractAddress_1_tokenId_1_blockNumber_-1_txId_-1__id_-1_isBlacklisted_1" to
                    Index()
                        .on(IndexedNft::owner.name, Sort.Direction.ASC)
                        .on(IndexedNft::contractAddress.name, Sort.Direction.ASC)
                        .on(IndexedNft::tokenId.name, Sort.Direction.ASC)
                        .on(IndexedNft::blockNumber.name, Sort.Direction.DESC)
                        .on(IndexedNft::txId.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
                        .on(IndexedNft::isBlacklisted.name, Sort.Direction.ASC),
            )
        )
    }
}
