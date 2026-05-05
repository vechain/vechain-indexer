package org.vechain.indexer.safe

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("safe")
open class SafeTxProposalConfig {

    @Bean
    open fun safeTxProposalIndexer(
        thorClient: ThorClient,
        processor: SafeTxProposalProcessor,
        @Qualifier("safeProxyIndexer") safeProxyIndexer: Indexer,
        @Value("\${indexer.start-block.safe-tx-proposals:0}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.SAFE_EMITTER_CONTRACT}") safeEmitterAddress: String,
    ): BlockIndexer {
        require(safeEmitterAddress.isNotBlank()) {
            "SAFE_EMITTER_CONTRACT must be configured when the 'safe' profile is active"
        }
        return IndexerFactory()
            .name(IndexerNames.SAFE_TX_PROPOSAL.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .abis("abis/safe-emitter")
            .abiContracts(listOf(safeEmitterAddress))
            .abiEventNames(listOf("SafeTxProposed", "SafeTxHashFields", "SafeBatchTxProposed"))
            .dependsOn(safeProxyIndexer)
            .build()
    }
}
