package org.vechain.indexer.safe.mongo

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexedDocument
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
        logger.debug("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.SAFE_MEMBERSHIP.NAME,
            SafeMembership::class.java,
            version,
        )
        ensureCollection()
        logger.debug("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                // Serves CURRENT scope (removedBlock IS NULL is equality) with the API's
                // addedBlock-desc sort: equality-equality-sort.
                buildIndex(
                    SafeMembership::owner.name to Sort.Direction.ASC,
                    SafeMembership::removedBlock.name to Sort.Direction.ASC,
                    SafeMembership::addedBlock.name to Sort.Direction.DESC,
                ),
                // Serves ALL and PAST scopes: equality on owner, then the addedBlock-desc sort.
                buildIndex(
                    SafeMembership::owner.name to Sort.Direction.ASC,
                    SafeMembership::addedBlock.name to Sort.Direction.DESC,
                ),
            )
        )
    }
}
