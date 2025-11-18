package org.vechain.indexer.b3tr.proposal

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

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-results")
@Configuration
open class ProposalResultCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
    @param:Value("\${indexer.version.b3tr-proposal-results}") private val version: Int,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        ProposalResult::class.java,
        ProposalResultArchive::class.java,
    ) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                indexerName = IndexerNames.PROPOSAL_RESULT,
                ProposalResult::class.java,
                version,
            )

        if (dropped) indexerVersionService.dropArchiveCollection(ProposalResultArchive::class.java)

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
                "createdAtBlockNumber_-1" to Index().on("createdAtBlockNumber", Sort.Direction.DESC),
            )
        )
    }
}
