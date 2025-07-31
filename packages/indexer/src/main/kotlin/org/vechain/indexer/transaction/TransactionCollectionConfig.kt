package org.vechain.indexer.transaction

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("transactions")
@Configuration
open class TransactionCollectionConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, IndexedTransaction::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.transactions}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            IndexedTransaction::class.java,
            version,
        )

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndex("tx_blockNumber_-1", Index().on("blockNumber", Sort.Direction.DESC))

        ensureIndex(
            "tx_origin_1_blockNumber_-1__id_-1",
            Index()
                .on("origin", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "tx_gasPayer_1_blockNumber_-1__id_-1",
            Index()
                .on("gasPayer", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "tx_origin_1_gasPayer_1_blockNumber_-1__id_-1",
            Index()
                .on("origin", Sort.Direction.ASC)
                .on("gasPayer", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )

        ensureIndex(
            "tx_clauses.to_1_blockNumber_-1__id_-1",
            Index()
                .on("clauses.to", Sort.Direction.ASC)
                .on("blockNumber", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC),
        )
    }
}
