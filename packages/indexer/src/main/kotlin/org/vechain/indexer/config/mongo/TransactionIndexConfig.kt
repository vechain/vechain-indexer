package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.IndexedTransaction

@Profile("transactions")
@Configuration
open class TransactionIndexConfig(private val mongoTemplate: MongoTemplate) : IndexConfig {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initIndexes() {
        val modelObj = IndexedTransaction::class.java

        val txBlockNumberIndex = "tx_blockNumber_-1"
        val txOriginBlockNumberIdIndex = "tx_origin_1_blockNumber_-1__id_-1"
        val txGasPayerBlockNumberIdIndex = "tx_gasPayer_1_blockNumber_-1__id_-1"
        val txOriginGasPayerBlockNumberIdIndex = "tx_origin_1_gasPayer_1_blockNumber_-1__id_-1"

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        logger.debug("Creating index: $txBlockNumberIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(Index().named(txBlockNumberIndex).on("blockNumber", Sort.Direction.DESC))

        logger.debug("Creating index: $txOriginBlockNumberIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(txOriginBlockNumberIdIndex)
                    .on("origin", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        logger.debug("Creating index: $txGasPayerBlockNumberIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(txGasPayerBlockNumberIdIndex)
                    .on("gasPayer", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        logger.debug("Creating index: $txOriginGasPayerBlockNumberIdIndex")
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(txOriginGasPayerBlockNumberIdIndex)
                    .on("origin", Sort.Direction.ASC)
                    .on("gasPayer", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        logger.info("Indexes for ${modelObj.simpleName} initialized successfully")
    }
}
