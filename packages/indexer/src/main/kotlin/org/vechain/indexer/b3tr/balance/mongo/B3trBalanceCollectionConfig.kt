package org.vechain.indexer.b3tr.balance.mongo

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
import org.vechain.indexer.b3tr.balance.B3trBalance
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("b3tr", "b3tr-balance")
@Configuration
open class B3trBalanceCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, B3trBalance::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.b3tr-balance}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.B3TR_BALANCE.NAME,
            B3trBalance::class.java,
            version,
        )

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "totalBalance_-1_id_1" to
                    Index()
                        .on(B3trBalance::totalBalance.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.ASC),
                "vot3Balance_-1_id_1" to
                    Index()
                        .on(B3trBalance::vot3Balance.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.ASC),
                "b3trBalance_-1_id_1" to
                    Index()
                        .on(B3trBalance::b3trBalance.name, Sort.Direction.DESC)
                        .on("_id", Sort.Direction.ASC),
                "blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
            )
        )
    }
}
