package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.model.AuthorityNodeEndorser
import org.vechain.indexer.service.IndexerVersionService

@Profile("authority-nodes")
@Configuration
open class AuthorityNodeEndorserConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, AuthorityNodeEndorser::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.AUTHORITY_NODE_ENDORSER}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged("authority_nodes", version)
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
    }
}
