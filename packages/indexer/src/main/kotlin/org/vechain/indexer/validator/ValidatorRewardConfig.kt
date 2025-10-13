package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("validator", "validator-reward")
open class ValidatorRewardConfig {
    @Bean
    open fun validatorRewardIndexer(
        thorClient: ThorClient,
        processor: ValidatorRewardProcessor,
        @Value("\${indexer.start-block.validator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        bEProperties: BusinessEventProperties,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.VALIDATOR_REWARD)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .callDataClauses(ValidatorUtils.buildVTHOTotalsClauses())
            .includeFullBlock()
            .build()
}
