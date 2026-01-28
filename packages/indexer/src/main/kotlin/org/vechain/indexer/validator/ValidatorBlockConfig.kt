package org.vechain.indexer.validator

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.validator.domain.ValidatorDecoder
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("validator", "validator-reward")
open class ValidatorBlockConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.validator-rewards:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.VALIDATOR_BLOCK,
            tableName = "validator_block_rewards",
            schemaResource = "db/tables/validator_block_rewards.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun validatorBlockIndexer(
        thorClient: ThorClient,
        processor: ValidatorBlockProcessor,
        @Value("\${indexer.start-block.validator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        bEProperties: BusinessEventProperties,
        @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
        getAllValidatorsAddress: String,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.VALIDATOR_BLOCK)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .callDataClauses(ValidatorDecoder.buildClauses(getAllValidatorsAddress))
            .includeFullBlock()
            .build()
}
