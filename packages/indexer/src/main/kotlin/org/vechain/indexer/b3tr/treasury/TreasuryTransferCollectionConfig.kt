package org.vechain.indexer.b3tr.treasury

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

@Profile("b3tr", "b3tr-treasury")
@Configuration
open class TreasuryTransferCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, TreasuryTransfer::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.b3tr-treasury}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.TREASURY_TRANSFER.NAME,
            TreasuryTransfer::class.java,
            version,
        )

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "treasury_transfer_category_1_blockTimestamp_-1__id_-1" to
                    Index()
                        .on(TreasuryTransfer::category.name, Sort.Direction.ASC)
                        .on(TreasuryTransfer::blockTimestamp.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
                "treasury_transfer_blockTimestamp_-1__id_-1" to
                    Index()
                        .on(TreasuryTransfer::blockTimestamp.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC),
            )
        )
    }
}
