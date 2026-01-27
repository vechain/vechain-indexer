package org.vechain.indexer.stargate.token

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.pruner.PostgresPruner
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.validator.domain.ValidatorDecoder

@Configuration
@Profile("stargate", "stargate-token")
open class StargateTokenConfig {
    @Bean
    open fun stargateTokenPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner =
        PostgresPruner(jdbcTemplate, namedJdbcTemplate, pruneBlockDepth, "stargate_tokens")

    @Bean
    open fun stargateIndexer(
        thorClient: ThorClient,
        processor: StargateTokenProcessor,
        @Value("\${indexer.start-block.stargate}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLogInterval: Long,
        stargateTokenPruner: PostgresPruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
        stargateDelegationContract: String,
        @Value("\${business-event.substitutions.STARGATE_CONTRACT}") stargateContract: String,
        @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
        getAllValidatorsContract: String,
        @Value("\${business-event.substitutions.NODE_MANAGEMENT_CONTRACT}")
        nodeManagementContract: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.STARGATE_TOKEN)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(stargateTokenPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .includeFullBlock()
            .abis("abis/stargate")
            .abiContracts(
                listOf(
                    stargateNftContract,
                    stargateDelegationContract,
                    stargateContract,
                    nodeManagementContract,
                )
            )
            .abiEventNames(
                listOf(
                    "TokenMinted",
                    "TokenBurned",
                    "DelegationInitiated",
                    "DelegationExitRequested",
                    "Transfer",
                    "TokenManagerAdded",
                    "TokenManagerRemoved",
                    "MaturityPeriodBoosted",
                    "ValidationSignaledExit",
                    "DelegationRewardsClaimed",
                    "BaseVTHORewardsClaimed",
                    "NodeDelegated",
                )
            )
            .callDataClauses(listOf(ValidatorDecoder.buildClauses(getAllValidatorsContract)[0]))
            .excludeVetTransfers()
            .build()
}
