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
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("nfts")
@Configuration
open class NftCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        IndexedNft::class.java,
        NftArchive::class.java,
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.nfts}") private var version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                IndexedNft::class.java,
                version,
            )

        if (dropped) indexerVersionService.dropArchiveCollection(NftArchive::class.java)

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "nft_blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
                "nft_contractAddress_1_tokenId_1" to
                    Index()
                        .on("contractAddress", Sort.Direction.ASC)
                        .on("tokenId", Sort.Direction.DESC)
                        .unique(),
                "nft_owner_1_blockNumber_-1_txId_-1__id_-1" to
                    Index()
                        .on("owner", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("txId", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "nft_contractAddress_1_blockNumber_-1_txId_-1__id_-1" to
                    Index()
                        .on("contractAddress", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("txId", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "nft_owner_1_contractAddress_1_blockNumber_-1_txId_-1__id_-1" to
                    Index()
                        .on("owner", Sort.Direction.ASC)
                        .on("contractAddress", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("txId", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "nft_owner_1_contractAddress_1_tokenId_1_blockNumber_-1_txId_-1__id_-1" to
                    Index()
                        .on("owner", Sort.Direction.ASC)
                        .on("contractAddress", Sort.Direction.ASC)
                        .on("tokenId", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("txId", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "nft_contractAddress_1" to Index().on("contractAddress", Sort.Direction.ASC),
            )
        )
    }
}
