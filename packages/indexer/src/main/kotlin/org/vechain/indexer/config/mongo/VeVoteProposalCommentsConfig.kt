package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.VevoteProposalComment
import org.vechain.indexer.service.IndexerVersionService

@Profile("vevote-events")
@Configuration
open class VeVoteProposalCommentsConfig(
    mongoTemplate: MongoTemplate,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, VevoteProposalComment::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.vevote_comments}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            "vevote_proposal_comments",
            version,
        )

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndex("voter_-1", Index().on("voter", Sort.Direction.DESC))

        ensureIndex("proposalId_-1", Index().on("proposalId", Sort.Direction.DESC))
    }
}
