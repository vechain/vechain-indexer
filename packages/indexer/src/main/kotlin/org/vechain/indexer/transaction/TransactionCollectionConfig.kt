package org.vechain.indexer.transaction

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

@Profile("transactions", "transaction")
@Configuration
open class TransactionCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, IndexedTransaction::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.transactions}") private val version: Int = 1

    override fun initCollection() {
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.TRANSACTION.NAME,
            IndexedTransaction::class.java,
            version,
        )
        ensureCollection()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(
                    IndexedTransaction::blockNumber.name to Sort.Direction.DESC,
                    IndexedTransaction::transactionIndex.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    IndexedTransaction::origin.name to Sort.Direction.ASC,
                    IndexedTransaction::blockNumber.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                ),
                buildIndex(
                    IndexedTransaction::gasPayer.name to Sort.Direction.ASC,
                    IndexedTransaction::blockNumber.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                ),
                buildIndex(
                    "clauses.to" to Sort.Direction.ASC,
                    IndexedTransaction::blockNumber.name to Sort.Direction.DESC,
                    "_id" to Sort.Direction.DESC,
                ),
            )
        )
    }
}
