package org.vechain.indexer.transaction

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

@Profile("transactions")
@Configuration
open class TransactionCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, IndexedTransaction::class.java) {
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

        ensureIndexes(
            listOf(
                Pair("tx_blockNumber_-1", Index().on("blockNumber", Sort.Direction.DESC)),
                Pair(
                    "tx_origin_1_blockNumber_-1__id_-1",
                    Index()
                        .on("origin", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                ),
                Pair(
                    "tx_gasPayer_1_blockNumber_-1__id_-1",
                    Index()
                        .on("gasPayer", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                ),
                Pair(
                    "tx_origin_1_gasPayer_1_blockNumber_-1__id_-1",
                    Index()
                        .on("origin", Sort.Direction.ASC)
                        .on("gasPayer", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                ),
                Pair(
                    "tx_clauses.to_1_blockNumber_-1__id_-1",
                    Index()
                        .on("clauses.to", Sort.Direction.ASC)
                        .on("blockNumber", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                ),
            )
        )
    }
}
