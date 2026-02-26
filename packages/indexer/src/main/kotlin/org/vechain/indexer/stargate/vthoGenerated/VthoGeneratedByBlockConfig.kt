package org.vechain.indexer.stargate.vthoGenerated

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

@Configuration
@Profile("stargate", "vtho-generated-by-block")
open class VthoGeneratedByBlockConfig {
    @Bean
    open fun vthoGeneratedByBlockIndexer(
        thorClient: ThorClient,
        processor: VthoGeneratedByBlockProcessor,
        @Value("\${indexer.start-block.vtho-generated-by-block}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}") stakerSC: String,
        bEProperties: BusinessEventProperties,
    ): BlockIndexer {
        val clauses = StargateUtils.buildIssuanceClause(stakerSC)

        return IndexerFactory()
            .name(IndexerNames.VTHO_GENERATED_BY_BLOCK.NAME)
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
