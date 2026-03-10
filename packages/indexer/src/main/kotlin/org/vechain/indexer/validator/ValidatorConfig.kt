package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.validator.domain.ValidatorDecoder.buildClauses

@Configuration
@Profile("validator", "validator-stats")
open class ValidatorConfig {

    @Bean
    open fun validatorIndexer(
        thorClient: ThorClient,
        processor: ValidatorProcessor,
        service: ValidatorService,
        @Value("\${indexer.start-block.validator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLogInterval: Long,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
        builtinStakerAddress: String,
        @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
        getAllValidatorsAddress: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.VALIDATOR.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .includeFullBlock()
            .abis("abis/stargate")
            .abiContracts(listOf(builtinStakerAddress))
            .abiEventNames(listOf("BeneficiarySet", "StakeDecreased", "ValidationWithdrawn"))
            .callDataClauses(buildClauses(getAllValidatorsAddress))
            .excludeVetTransfers()
            .build()
}
