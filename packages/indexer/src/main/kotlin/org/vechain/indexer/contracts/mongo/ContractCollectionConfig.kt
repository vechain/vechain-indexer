package org.vechain.indexer.contracts.mongo

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.contracts.Contract
import org.vechain.indexer.contracts.ContractArchive
import org.vechain.indexer.version.IndexerVersionService

@Profile("contracts", "contract")
@Configuration
open class ContractCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        Contract::class.java,
        ContractArchive::class.java,
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.contracts:1}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                indexerName = IndexerNames.CONTRACTS_INDEXER,
                Contract::class.java,
                version,
            )

        if (dropped) indexerVersionService.dropArchiveCollection(ContractArchive::class.java)

        ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "blockNumber_-1" to
                    Index().on(IndexedDocument::blockNumber.name, Sort.Direction.DESC),
                // Supports query: deployer == address, sorted by createdOn desc
                "deployer_1_createdOn_-1" to
                    Index()
                        .on(Contract::deployer.name, Sort.Direction.ASC)
                        .on(Contract::createdOn.name, Sort.Direction.DESC),
                // Supports query: master == address, sorted by createdOn desc
                "master_1_createdOn_-1" to
                    Index()
                        .on(Contract::master.name, Sort.Direction.ASC)
                        .on(Contract::createdOn.name, Sort.Direction.DESC),
            )
        )
    }
}
