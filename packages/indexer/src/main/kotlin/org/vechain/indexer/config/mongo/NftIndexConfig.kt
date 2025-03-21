package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initIndexes() {
        val modelObj = IndexedNFT::class.java
        val archiveObj = NFTArchive::class.java

        val nftBlockNumberIndex = "nft_blockNumber_-1"
        val nftContractAddressTokenIdIndex = "nft_contractAddress_1_tokenId_1"
        val nftOwnerBlockNumberTxIdIdIndex = "nft_owner_1_blockNumber_-1_txId_-1__id_-1"
        val nftContractAddressBlockNumberTxIdIdIndex =
            "nft_contractAddress_1_blockNumber_-1_txId_-1__id_-1"
        val nftOwnerContractAddressBlockNumberTxIdIdIndex =
            "nft_owner_1_contractAddress_1_blockNumber_-1_txId_-1__id_-1"
        val nftOwnerContractAddressTokenIdBlockNumberTxIdIdIndex =
            "nft_owner_1_contractAddress_1_tokenId_1_blockNumber_-1_txId_-1__id_-1"
        val dataBlockNumberIndex = "data.blockNumber_-1"

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        // Ensure NFT collection indexes
        if (!mongoTemplate.collectionExists(modelObj)) {
            logger.info(
                "Collection for ${modelObj.simpleName} does not exist. Creating collection."
            )
            mongoTemplate.createCollection(modelObj)
        } else {
            logger.debug("Collection for ${modelObj.simpleName} already exists.")
        }

        logger.debug("Creating index: $nftBlockNumberIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(nftBlockNumberIndex)
                    .on("blockNumber", Sort.Direction.DESC)
                    .background()
            )

        logger.debug("Creating index: $nftContractAddressTokenIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(nftContractAddressTokenIdIndex)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("tokenId", Sort.Direction.DESC)
                    .unique()
                    .background()
            )

        logger.debug("Creating index: $nftOwnerBlockNumberTxIdIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(nftOwnerBlockNumberTxIdIdIndex)
                    .on("owner", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
                    .background()
            )

        logger.debug("Creating index: $nftContractAddressBlockNumberTxIdIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(nftContractAddressBlockNumberTxIdIdIndex)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
                    .background()
            )

        logger.debug("Creating index: $nftOwnerContractAddressBlockNumberTxIdIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(nftOwnerContractAddressBlockNumberTxIdIdIndex)
                    .on("owner", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
                    .background()
            )

        logger.debug("Creating index: $nftOwnerContractAddressTokenIdBlockNumberTxIdIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(nftOwnerContractAddressTokenIdBlockNumberTxIdIdIndex)
                    .on("owner", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("tokenId", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
                    .background()
            )

        // Ensure Archive collection index
        if (!mongoTemplate.collectionExists(archiveObj)) {
            logger.info(
                "Collection for ${archiveObj.simpleName} does not exist. Creating collection."
            )
            mongoTemplate.createCollection(archiveObj)
        } else {
            logger.debug("Collection for ${archiveObj.simpleName} already exists.")
        }

        logger.debug("Creating index: $dataBlockNumberIndex")
        mongoTemplate
            .indexOps(archiveObj)
            .ensureIndex(
                Index()
                    .named(dataBlockNumberIndex)
                    .on("data.blockNumber", Sort.Direction.DESC)
                    .background()
            )

        logger.info("Indexes for ${modelObj.simpleName} initialized successfully")
    }
}
