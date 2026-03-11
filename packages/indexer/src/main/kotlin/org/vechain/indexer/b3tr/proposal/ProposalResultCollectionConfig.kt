package org.vechain.indexer.b3tr.proposal

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
) : CollectionConfig(mongoTemplate, appCoroutineScope, ProposalResult::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.PROPOSAL_RESULT.NAME,
            ProposalResult::class.java,
            version,
        )
        this.ensureCollection()
        logger.info("Initializing indexes for ${modelObj.simpleName}")
        ensureIndexes(
            listOf(
                "state_1_createdAtBlockNumber_-1" to
                    Index()
                        .on(ProposalResult::state.name, Sort.Direction.ASC)
                        .on(ProposalResult::createdAtBlockNumber.name, Sort.Direction.DESC),
                "blockNumber_-1" to Index().on("blockNumber", Sort.Direction.DESC),
            )
        )
    }
}
