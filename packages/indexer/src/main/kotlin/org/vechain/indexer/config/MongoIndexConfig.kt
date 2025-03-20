package org.vechain.indexer.config

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index

@Configuration
open class MongoIndexConfig(private val mongoTemplate: MongoTemplate) {

    @PostConstruct
    fun initHistoryIndexes() {
        val collection = "history_events"

        mongoTemplate
            .indexOps(collection)
            .ensureIndex(Index().named("blockNumber_1").on("blockNumber", Sort.Direction.ASC))

        mongoTemplate
            .indexOps(collection)
            .ensureIndex(
                Index()
                    .named("to_1_contractAddress_1_blockTimestamp_-1")
                    .on("to", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(collection)
            .ensureIndex(
                Index()
                    .named("from_1_contractAddress_1_blockTimestamp_-1")
                    .on("from", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(collection)
            .ensureIndex(
                Index()
                    .named("origin_1_contractAddress_1_blockTimestamp_-1")
                    .on("origin", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(collection)
            .ensureIndex(
                Index()
                    .named("from_1_blockTimestamp_-1_eventName_1")
                    .on("from", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )

        mongoTemplate
            .indexOps(collection)
            .ensureIndex(
                Index()
                    .named("to_1_blockTimestamp_-1_eventName_1")
                    .on("to", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )

        mongoTemplate
            .indexOps(collection)
            .ensureIndex(
                Index()
                    .named("origin_1_blockTimestamp_-1_eventName_1")
                    .on("origin", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )

        mongoTemplate
            .indexOps(collection)
            .ensureIndex(
                Index()
                    .named("gasPayer_1_blockTimestamp_-1_eventName_1")
                    .on("gasPayer", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )
    }

    @PostConstruct
    fun initNftIndexes() {
        val nftCollection = "nfts"
        val archiveCollection = "nft_archive"

        // Ensure NFT collection indexes
        mongoTemplate
            .indexOps(nftCollection)
            .ensureIndex(Index().named("nft_blockNumber_-1").on("blockNumber", Sort.Direction.DESC))

        mongoTemplate
            .indexOps(nftCollection)
            .ensureIndex(
                Index()
                    .named("nft_contractAddress_1_tokenId_1")
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("tokenId", Sort.Direction.DESC)
                    .unique()
            )

        mongoTemplate
            .indexOps(nftCollection)
            .ensureIndex(
                Index()
                    .named("nft_owner_1_blockNumber_-1_txId_-1__id_-1")
                    .on("owner", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(nftCollection)
            .ensureIndex(
                Index()
                    .named("nft_contractAddress_1_blockNumber_-1_txId_-1__id_-1")
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(nftCollection)
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
            .indexOps(nftCollection)
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
            .indexOps(archiveCollection)
            .ensureIndex(
                Index().named("data.blockNumber_-1").on("data.blockNumber", Sort.Direction.DESC)
            )
    }

    @PostConstruct
    fun initTransactionIndexes() {
        val transactionCollection = "transactions"

        // Ensure Transaction indexes
        mongoTemplate
            .indexOps(transactionCollection)
            .ensureIndex(Index().named("tx_blockNumber_-1").on("blockNumber", Sort.Direction.DESC))

        mongoTemplate
            .indexOps(transactionCollection)
            .ensureIndex(
                Index()
                    .named("tx_origin_1_blockNumber_-1__id_-1")
                    .on("origin", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(transactionCollection)
            .ensureIndex(
                Index()
                    .named("tx_gasPayer_1_blockNumber_-1__id_-1")
                    .on("gasPayer", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(transactionCollection)
            .ensureIndex(
                Index()
                    .named("tx_origin_1_gasPayer_1_blockNumber_-1__id_-1")
                    .on("origin", Sort.Direction.ASC)
                    .on("gasPayer", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )
    }

    @PostConstruct
    fun initIndexes() {
        val transferCollection = "transfer_events"

        // Ensure Transfer Events indexes
        mongoTemplate
            .indexOps(transferCollection)
            .ensureIndex(
                Index().named("transfer_blockNumber_-1").on("blockNumber", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(transferCollection)
            .ensureIndex(
                Index()
                    .named("transfer_to_1_blockNumber_-1_txId_-1__id_-1")
                    .on("to", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(transferCollection)
            .ensureIndex(
                Index()
                    .named("transfer_from_1_blockNumber_-1_txId_-1__id_-1")
                    .on("from", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(transferCollection)
            .ensureIndex(
                Index()
                    .named("transfer_tokenAddress_1_blockNumber_-1_txId_-1__id_-1")
                    .on("tokenAddress", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(transferCollection)
            .ensureIndex(
                Index()
                    .named(
                        "transfer_tokenAddress_1_eventType_1_to_1_1_blockNumber_-1_txId_-1__id_-1"
                    )
                    .on("tokenAddress", Sort.Direction.ASC)
                    .on("eventType", Sort.Direction.ASC)
                    .on("to", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(transferCollection)
            .ensureIndex(
                Index()
                    .named(
                        "transfer_tokenAddress_1_eventType_1_from_1_1_blockNumber_-1_txId_-1__id_-1"
                    )
                    .on("tokenAddress", Sort.Direction.ASC)
                    .on("eventType", Sort.Direction.ASC)
                    .on("from", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )
    }
}
