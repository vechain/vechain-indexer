package org.vechain.indexer.safe.mongo

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
import org.vechain.indexer.safe.SafeMembership
import org.vechain.indexer.version.IndexerVersionService

@Profile("safe")
@Configuration
open class SafeMembershipCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, SafeMembership::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.safe-membership:1}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.SAFE_MEMBERSHIP.NAME,
            SafeMembership::class.java,
            version,
        )
        ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                // Serves CURRENT scope (removedBlock IS NULL is equality) with the API's
                // addedBlock-desc sort: equality-equality-sort.
                "owner_1_removedBlock_1_addedBlock_-1" to
                    Index()
                        .on(SafeMembership::owner.name, Sort.Direction.ASC)
                        .on(SafeMembership::removedBlock.name, Sort.Direction.ASC)
                        .on(SafeMembership::addedBlock.name, Sort.Direction.DESC),
                // Serves ALL and PAST scopes: equality on owner, then the addedBlock-desc sort.
                "owner_1_addedBlock_-1" to
                    Index()
                        .on(SafeMembership::owner.name, Sort.Direction.ASC)
                        .on(SafeMembership::addedBlock.name, Sort.Direction.DESC),
            )
        )
    }
}
