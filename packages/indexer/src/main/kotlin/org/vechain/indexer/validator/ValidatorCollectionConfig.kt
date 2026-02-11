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
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("validator", "validator-stats")
@Configuration
open class ValidatorCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) :
    CollectionConfig(
        mongoTemplate,
        appCoroutineScope,
        Validator::class.java,
        ValidatorArchive::class.java,
    ) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.validator}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        val dropped =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                IndexerNames.VALIDATOR.NAME,
                Validator::class.java,
                version,
            )

        if (dropped) indexerVersionService.dropArchiveCollection(ValidatorArchive::class.java)

        this.ensureCollection()

        logger.info("Initializing indexes for ${modelObj.simpleName}")

        ensureIndexes(
            listOf(
                "endorser_1" to Index().on(Validator::endorser.name, Sort.Direction.ASC),
                "validatorTvl_-1" to Index().on(Validator::validatorTvl.name, Sort.Direction.DESC),
                "delegatorTvl_-1" to Index().on(Validator::delegatorTvl.name, Sort.Direction.DESC),
                "totalTvl_-1" to Index().on(Validator::totalTvl.name, Sort.Direction.DESC),
                "blockProbability" to
                    Index().on(Validator::blockProbability.name, Sort.Direction.DESC),
            )
        )
    }
}
