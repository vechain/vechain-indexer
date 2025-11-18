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
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@Profile("validator", "validator-reward")
@Configuration
open class ValidatorBlockCollectionConfig(
    mongoTemplate: MongoTemplate,
    appCoroutineScope: CoroutineScope,
    private val indexerVersionService: IndexerVersionService,
) : CollectionConfig(mongoTemplate, appCoroutineScope, ValidatorBlock::class.java) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Value("\${indexer.version.validator-rewards}") private val version: Int = 1

    @PostConstruct
    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")

        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.VALIDATOR_BLOCK,
            ValidatorBlock::class.java,
            version,
        )

        ensureCollection()

        // Ensure indexes
        ensureIndexes(
            listOf(
                // For global queries (all validators, sorted by timestamp)
                "blockTimestamp_-1" to
                    Index().on(IndexedDocument::blockTimestamp.name, Sort.Direction.DESC),
                // For per-validator queries sorted by timestamp
                "validator_1_blockTimestamp_-1" to
                    Index()
                        .on(ValidatorBlock::validator.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockTimestamp.name, Sort.Direction.DESC),
                // For per-validator queries sorted by blockNumber
                "validator_1_blockNumber_-1" to
                    Index()
                        .on(ValidatorBlock::validator.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockNumber.name, Sort.Direction.DESC),
                "isMonthly_1_status_1_validator_1_blockTimestamp_1" to
                    Index()
                        .on(ValidatorBlock::isMonthly.name, Sort.Direction.ASC)
                        .on(ValidatorBlock::status.name, Sort.Direction.ASC)
                        .on(ValidatorBlock::validator.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockTimestamp.name, Sort.Direction.ASC),
                "isWeekly_1_status_1_validator_1_blockTimestamp_1" to
                    Index()
                        .on(ValidatorBlock::isWeekly.name, Sort.Direction.ASC)
                        .on(ValidatorBlock::status.name, Sort.Direction.ASC)
                        .on(ValidatorBlock::validator.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockTimestamp.name, Sort.Direction.ASC),
                "status_1_validator_1_blockTimestamp_1" to
                    Index()
                        .on(ValidatorBlock::status.name, Sort.Direction.ASC)
                        .on(ValidatorBlock::validator.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockTimestamp.name, Sort.Direction.ASC),
                "isDaily_1_status_1_validator_1_blockTimestamp_1" to
                    Index()
                        .on(ValidatorBlock::isDaily.name, Sort.Direction.ASC)
                        .on(ValidatorBlock::status.name, Sort.Direction.ASC)
                        .on(ValidatorBlock::validator.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockTimestamp.name, Sort.Direction.ASC),
                "blockNumber_-1" to
                    Index().on(IndexedDocument::blockNumber.name, Sort.Direction.DESC),
                "status_1_blockNumber_-1" to
                    Index()
                        .on(ValidatorBlock::status.name, Sort.Direction.ASC)
                        .on(IndexedDocument::blockNumber.name, Sort.Direction.DESC),
            )
        )
    }
}
