package org.vechain.indexer.stargate.vthoClaimed

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PostgresPruner
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("stargate", "vtho-claimed-by-account")
open class VthoClaimedByAccountConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.stargate-vtho-claimed-by-account:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.VTHO_CLAIMED_BY_ACCOUNT,
            tableName = "stargate_vtho_claimed_by_account",
            schemaResource = "db/tables/stargate_vtho_claimed_by_account.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun vthoClaimByAccountPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner =
        PostgresPruner(
            jdbcTemplate,
            namedJdbcTemplate,
            pruneBlockDepth,
            "stargate_vtho_claimed_by_account",
        )

    @Bean
    open fun vthoClaimedByAccountIndexer(
        thorClient: ThorClient,
        processor: VthoClaimedByAccountProcessor,
        vthoClaimByAccountPruner: PostgresPruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.stargate}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
        stargateDelegationContract: String,
        @Value("\${business-event.substitutions.STARGATE_CONTRACT}") stargateContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.VTHO_CLAIMED_BY_ACCOUNT)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(vthoClaimByAccountPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/stargate", "abis/stargate")
            .businessEventNames(
                listOf(
                    "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
                    "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY",
                    "STARGATE_CLAIM_REWARDS",
                )
            )
            .businessEventContracts(
                listOf(
                    stargateNftContract,
                    stargateDelegationContract,
                    stargateContract,
                    VTHO_CONTRACT_ADDRESS,
                )
            )
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
