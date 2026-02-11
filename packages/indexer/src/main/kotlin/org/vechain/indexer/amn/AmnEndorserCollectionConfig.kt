package org.vechain.indexer.amn

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

@Profile("authority-nodes")
@Configuration
open class AmnEndorserCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, AmnEndorser::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.authority-node-endorser}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.AUTHORITY_NODE.NAME,
            clazz = AmnEndorser::class.java,
            newVersion = version,
        )
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf("endorser_1" to Index().on(AmnEndorser::endorser.name, Sort.Direction.ASC))
        )
    }
}
