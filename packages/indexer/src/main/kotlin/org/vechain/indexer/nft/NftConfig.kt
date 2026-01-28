package org.vechain.indexer.nft

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
import org.vechain.indexer.pruner.PostgresPruner
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("nfts")
open class NftConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.nfts:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.NFT,
            tableName = "nfts",
            schemaResource = "db/tables/nfts.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun nftPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner = PostgresPruner(jdbcTemplate, namedJdbcTemplate, pruneBlockDepth, "nfts")

    @Bean
    open fun nftIndexer(
        thorClient: ThorClient,
        processor: NftProcessor,
        nftPruner: PostgresPruner,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.nfts}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.nfts}") syncBlockBatchSize: Long,
        @Value("\${indexer.blacklist.contract-address}") blacklistContract: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.NFT)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(nftPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/nft")
            .abiEventNames(listOf("Transfer"))
            .businessEvents("business-events/nft", "abis/nft")
            .businessEventNames(listOf("NFT_Blacklisted", "NFT_Whitelisted"))
            .businessEventContracts(listOf(blacklistContract))
            .businessEventSubstitutionParams(
                mapOf("BLACKLIST_CONTRACT_ADDRESS" to blacklistContract)
            )
            .excludeVetTransfers()
            .build()
}
