package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.validator.domain.ValidatorDecoder

@Configuration
@Profile("validator", "validator-reward")
open class ValidatorBlockConfig {
    @Bean
    open fun validatorBlockIndexer(
        thorClient: ThorClient,
        processor: ValidatorBlockProcessor,
        @Value("\${indexer.start-block.validator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
        getAllValidatorsAddress: String,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.VALIDATOR_BLOCK)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .callDataClauses(listOf(ValidatorDecoder.buildClauses(getAllValidatorsAddress)[0]))
            .includeFullBlock()
            .excludeVetTransfers()
            .build()
}
