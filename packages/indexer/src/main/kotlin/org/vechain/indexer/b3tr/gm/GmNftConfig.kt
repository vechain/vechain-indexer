package org.vechain.indexer.b3tr.gm

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
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("b3tr", "b3tr-gm-nft")
open class GmNftConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.b3tr-gm-nft:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.GM_NFT,
            tableName = "b3tr_gm_nfts",
            schemaResource = "db/tables/b3tr_gm_nfts.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun gmNftPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner =
        PostgresPruner(jdbcTemplate, namedJdbcTemplate, pruneBlockDepth, "b3tr_gm_nfts")

    @Bean
    open fun gmNftIndexer(
        thorClient: ThorClient,
        processor: GmNftProcessor,
        gmNftPruner: PostgresPruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.b3tr}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContractAddress: String,
        @Value("\${business-event.substitutions.VOTER_REWARDS_CONTRACT}")
        voterRewardsContractAddress: String,
        @Value("\${business-event.substitutions.GM_NFT_CONTRACT}") gmNftContractAddress: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.GM_NFT)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(gmNftPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(
                listOf(
                    "B3TR_GmTransfer",
                    "B3TR_GmBurned",
                    "B3TR_GmMinted",
                    "B3TR_GmNodeAttached",
                    "B3TR_GmNodeDetached",
                    "B3TR_GmUpgrade",
                    "B3TR_GmNodeLevel",
                )
            )
            .businessEventContracts(
                listOf(b3trContractAddress, voterRewardsContractAddress, gmNftContractAddress)
            )
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
