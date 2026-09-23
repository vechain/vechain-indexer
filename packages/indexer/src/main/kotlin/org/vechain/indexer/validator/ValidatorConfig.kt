package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("validator", "history")
open class ValidatorConfig {

    @Bean
    open fun validatorIndexer(
        thorClient: ThorClient,
        processor: ValidatorProcessor,
        @Value("\${indexer.start-block.validator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLogInterval: Long,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
        builtinStakerAddress: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.VALIDATOR.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .includeFullBlock()
            .callDataClauses(listOf(TokenRewardService.energyTotalSupplyClause()))
            .abis("abis/stargate")
            .abiContracts(listOf(builtinStakerAddress))
            .abiEventNames(
                listOf(
                    "BeneficiarySet",
                    "StakeDecreased",
                    "StakeIncreased",
                    "ValidationQueued",
                    "ValidationSignaledExit",
                    "ValidationWithdrawn",
                )
            )
            .excludeVetTransfers()
            .build()
}
