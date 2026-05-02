package org.vechain.indexer.vevote

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("vevote", "vevote-comments")
@Configuration
open class VeVoteCommentCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, VeVoteProposalComment::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    @Value("\${indexer.version.vevote-comments}") private val version: Int = 1

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.VEVOTE_COMMENT.NAME,
            VeVoteProposalComment::class.java,
            version,
        )
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                buildIndex(VeVoteProposalComment::voter.name to Sort.Direction.DESC),
                buildIndex(VeVoteProposalComment::proposalId.name to Sort.Direction.DESC),
            )
        )
    }
}
