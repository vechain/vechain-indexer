package org.vechain.indexer.vevote

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("vevote", "vevote-results")
@Configuration
open class VeVoteResultCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    @param:Value("\${indexer.version.vevote-results}") private val version: Int,
    private val indexerVersionService: IndexerVersionService,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        VeVoteProposalResult::class.java,
        VeVoteProposalResultArchive::class.java,
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                indexerName = "VeVoteResultIndexer",
                VeVoteProposalResult::class.java,
                version,
            )

        if (dropped)
            indexerVersionService.dropArchiveCollection(VeVoteProposalResultArchive::class.java)

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "support_1" to Index().on("support", Sort.Direction.ASC),
                "proposalId_1" to Index().on("proposalId", Sort.Direction.ASC),
            )
        )
    }
}
