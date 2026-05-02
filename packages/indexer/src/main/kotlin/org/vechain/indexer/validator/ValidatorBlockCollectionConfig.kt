package org.vechain.indexer.validator

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

    override fun initCollection() {
        logger.info("Check collection version for ${modelObj.simpleName}")
        indexerVersionService.checkAndResetCollectionIfVersionChanged(
            indexerName = IndexerNames.VALIDATOR_BLOCK.NAME,
            ValidatorBlock::class.java,
            version,
        )
        ensureCollection()
        // Ensure indexes
        ensureIndexes(
            listOf(
                // For global queries (all validators, sorted by timestamp)
                buildIndex(IndexedDocument::blockTimestamp.name to Sort.Direction.DESC),
                // For per-validator queries sorted by timestamp
                buildIndex(
                    ValidatorBlock::validator.name to Sort.Direction.ASC,
                    IndexedDocument::blockTimestamp.name to Sort.Direction.DESC,
                ),
                // For per-validator queries sorted by blockNumber
                buildIndex(
                    ValidatorBlock::validator.name to Sort.Direction.ASC,
                    IndexedDocument::blockNumber.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    ValidatorBlock::isMonthly.name to Sort.Direction.ASC,
                    ValidatorBlock::status.name to Sort.Direction.ASC,
                    ValidatorBlock::validator.name to Sort.Direction.ASC,
                    IndexedDocument::blockTimestamp.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    ValidatorBlock::isWeekly.name to Sort.Direction.ASC,
                    ValidatorBlock::status.name to Sort.Direction.ASC,
                    ValidatorBlock::validator.name to Sort.Direction.ASC,
                    IndexedDocument::blockTimestamp.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    ValidatorBlock::status.name to Sort.Direction.ASC,
                    ValidatorBlock::validator.name to Sort.Direction.ASC,
                    IndexedDocument::blockTimestamp.name to Sort.Direction.ASC,
                ),
                buildIndex(
                    ValidatorBlock::status.name to Sort.Direction.ASC,
                    IndexedDocument::blockNumber.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    ValidatorBlock::isDaily.name to Sort.Direction.ASC,
                    ValidatorBlock::status.name to Sort.Direction.ASC,
                    ValidatorBlock::validator.name to Sort.Direction.ASC,
                    IndexedDocument::blockTimestamp.name to Sort.Direction.ASC,
                ),
                // For block-rewards queries filtered by validator + status, sorted by blockNumber
                buildIndex(
                    ValidatorBlock::validator.name to Sort.Direction.ASC,
                    ValidatorBlock::status.name to Sort.Direction.ASC,
                    IndexedDocument::blockNumber.name to Sort.Direction.DESC,
                ),
                buildIndex(
                    ValidatorBlock::validator.name to Sort.Direction.ASC,
                    ValidatorBlock::status.name to Sort.Direction.ASC,
                    IndexedDocument::blockNumber.name to Sort.Direction.ASC,
                ),
            )
        )
    }
}
