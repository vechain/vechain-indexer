package org.vechain.indexer.validator

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

@Profile("validator")
@Configuration
open class ValidatorCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, Validator::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.validator}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            Validator::class.java,
            version,
        )

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "endorser_1" to Index().on("endorser", Sort.Direction.ASC),
                "delegationIdList_1" to Index().on("delegationIdList", Sort.Direction.ASC),
                "validatorTvl_-1" to Index().on("validatorTvl", Sort.Direction.DESC),
                "delegatorTvl_-1" to Index().on("delegatorTvl", Sort.Direction.DESC),
                "totalTvl_-1" to Index().on("totalTvl", Sort.Direction.DESC),
                "blockProbability" to Index().on("blockProbability", Sort.Direction.DESC),
            )
        )
    }
}
