package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

/**
 * V2 delegation indexer wiring.
 *
 * Differences from V1 [DelegationConfig]:
 * - **No `callDataClauses`** — drops the dependency on the deployed `GetValidators` aggregator,
 *   which unblocks solo / custom networks.
 * - **`dependsOn(validatorIndexer)`** — flips the V1 ordering so the V2 delegation indexer reads
 *   already-persisted `Validator` state for cycle math. The cycle is gone: validator no longer
 *   depends on delegation, delegation now depends on validator, and neither calls the chain for the
 *   other's data.
 */
@Configuration
@Profile("delegation")
open class DelegationConfig {

    @Bean
    open fun delegationIndexer(
        thorClient: ThorClient,
        processor: DelegationProcessor,
        @Qualifier("validatorIndexer") validatorIndexer: Indexer,
        @Value("\${indexer.start-block.delegation}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLogInterval: Long,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
        builtinStakerAddress: String,
        @Value("\${business-event.substitutions.STARGATE_CONTRACT}") stargateContract: String,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}") stargateNftContract: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.DELEGATION.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .includeFullBlock()
            .abis("abis/stargate")
            .abiContracts(listOf(builtinStakerAddress, stargateContract, stargateNftContract))
            .abiEventNames(
                listOf(
                    "DelegationInitiated",
                    "DelegationExitRequested",
                    "DelegationWithdrawn",
                    "DelegationRewardsClaimed",
                    "ValidationSignaledExit",
                    "Transfer",
                )
            )
            .dependsOn(validatorIndexer)
            .excludeVetTransfers()
            .build()
}
