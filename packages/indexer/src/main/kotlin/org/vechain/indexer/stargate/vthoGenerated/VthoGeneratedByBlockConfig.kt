package org.vechain.indexer.stargate.vthoGenerated

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.stargate.StargateUtils
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Configuration
@Profile("stargate", "vtho-generated-by-block")
open class VthoGeneratedByBlockConfig(private val indexerVersionService: IndexerVersionService) {
    @Value("\${indexer.version.stargate-vtho-generated-by-block:1}") private var version: Int = 1

    @PostConstruct
    open fun initVersionCheck() {
        indexerVersionService.ensureTableExists(
            indexerName = IndexerNames.VTHO_GENERATED_BY_BLOCK,
            tableName = "stargate_vtho_generated_by_block",
            schemaResource = "db/tables/stargate_vtho_generated_by_block.sql",
            newVersion = version,
        )
    }

    @Bean
    open fun vthoGeneratedByBlockIndexer(
        thorClient: ThorClient,
        processor: VthoGeneratedByBlockProcessor,
        @Value("\${indexer.start-block.delegation}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}") stakerSC: String,
        bEProperties: BusinessEventProperties,
    ): BlockIndexer {
        val clauses = StargateUtils.buildIssuanceClause(stakerSC)

        return IndexerFactory()
            .name(IndexerNames.VTHO_GENERATED_BY_BLOCK)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .callDataClauses(clauses)
            .includeFullBlock()
            .build()
    }
}
