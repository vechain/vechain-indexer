package org.vechain.indexer.safe

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

/** Safes are not known in advance: logs match on signature, the processor checks the emitter. */
@Configuration
@Profile("safe")
open class SafeConfig {

    @Bean
    open fun safeIndexer(
        thorClient: ThorClient,
        processor: SafeProcessor,
        @Value("\${indexer.start-block.safe:0}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.SAFE_PROXY_FACTORY_CONTRACT}")
        safeProxyFactoryAddress: String,
        @Value("\${business-event.substitutions.SAFE_EMITTER_CONTRACT}") safeEmitterAddress: String,
    ): Indexer {
        require(safeProxyFactoryAddress.isNotBlank() && safeEmitterAddress.isNotBlank()) {
            "SAFE_PROXY_FACTORY_CONTRACT and SAFE_EMITTER_CONTRACT must be configured when the " +
                "'safe' profile is active"
        }
        return IndexerFactory()
            .name(IndexerNames.SAFE.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .abis("abis/safe")
            .abiEventNames(
                listOf(SafeEventUtils.PROXY_CREATION) +
                    SafeEventUtils.MEMBERSHIP_EVENTS +
                    SafeEventUtils.TX_STATE_EVENTS +
                    SafeEventUtils.PROPOSAL_EVENTS
            )
            .excludeVetTransfers()
            .build()
    }
}
