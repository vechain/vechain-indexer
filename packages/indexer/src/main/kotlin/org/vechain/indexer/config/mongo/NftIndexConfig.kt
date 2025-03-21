package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.NFTArchive

@Profile("nft-events")
@Configuration
open class NftIndexConfig(private val mongoTemplate: MongoTemplate) : IndexConfig {

    @PostConstruct
    override fun initIndexes() {
        val modelObj = IndexedNFT::class.java
        val archiveObj = NFTArchive::class.java

        // Ensure NFT collection indexes
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(Index().named("nft_blockNumber_-1").on("blockNumber", Sort.Direction.DESC))

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("nft_contractAddress_1_tokenId_1")
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("tokenId", Sort.Direction.DESC)
                    .unique()
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("nft_owner_1_blockNumber_-1_txId_-1__id_-1")
                    .on("owner", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("nft_contractAddress_1_blockNumber_-1_txId_-1__id_-1")
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("nft_owner_1_contractAddress_1_blockNumber_-1_txId_-1__id_-1")
                    .on("owner", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("nft_owner_1_contractAddress_1_tokenId_1_blockNumber_-1_txId_-1__id_-1")
                    .on("owner", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("tokenId", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        // Ensure Archive collection index
        mongoTemplate
            .indexOps(archiveObj)
            .ensureIndex(
                Index().named("data.blockNumber_-1").on("data.blockNumber", Sort.Direction.DESC)
            )
    }
}
