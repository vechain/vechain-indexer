package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.service.IndexerVersionService

@Profile("nft-events")
@Configuration
open class NftCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, IndexedNFT::class.java, NFTArchive::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.nfts}") private var version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                IndexedNFT::class.java,
                version,
            )

        if (dropped) indexerVersionService.dropArchiveCollection(NFTArchive::class.java)

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndex("nft_blockNumber_-1", Index().on("blockNumber", Sort.Direction.DESC))

        ensureIndex(
            "nft_contractAddress_1_tokenId_1",
            Index()
                .on("contractAddress", Sort.Direction.ASC)
                .on("tokenId", Sort.Direction.DESC)
                .unique(),
        )

        ensureIndex(
            "nft_owner_1_blockNumber_-1_txId_-1__id_-1",
            Index()
                .on("owner", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("txId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "nft_contractAddress_1_blockNumber_-1_txId_-1__id_-1",
            Index()
                .on("contractAddress", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("txId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "nft_owner_1_contractAddress_1_blockNumber_-1_txId_-1__id_-1",
            Index()
                .on("owner", Sort.Direction.ASC)
                .on("contractAddress", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("txId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "nft_owner_1_contractAddress_1_tokenId_1_blockNumber_-1_txId_-1__id_-1",
            Index()
                .on("owner", Sort.Direction.ASC)
                .on("contractAddress", Sort.Direction.ASC)
                .on("tokenId", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("txId", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "nft_isBlacklisted_1_contractAddress_1",
            Index()
                .on("isBlacklisted", Sort.Direction.ASC)
                .on("contractAddress", Sort.Direction.ASC),
        )
    }
}
