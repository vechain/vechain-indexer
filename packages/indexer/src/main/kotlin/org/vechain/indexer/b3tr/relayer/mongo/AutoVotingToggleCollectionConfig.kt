package org.vechain.indexer.b3tr.relayer.mongo

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.relayer.AutoVotingToggle
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-auto-voting-toggles")
open class AutoVotingToggleCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    @param:Value("\${indexer.version.b3tr-auto-voting-toggles}") private val version: Int,
) : CollectionConfig(mongoTemplate, appCoroutineScope, AutoVotingToggle::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.AUTO_VOTING_TOGGLE.NAME,
            AutoVotingToggle::class.java,
            version,
        )
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(IndexedDocument::blockNumber.name to Sort.Direction.DESC),
                buildIndex(
                    AutoVotingToggle::activeFromRound.name to Sort.Direction.DESC,
                    AutoVotingToggle::address.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    AutoVotingToggle::address.name to Sort.Direction.ASC,
                    AutoVotingToggle::activeFromRound.name to Sort.Direction.DESC,
                ),
            )
        )
    }
}
