package org.vechain.indexer.b3tr.treasury

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-treasury")
open class TreasuryTransferConfig {

    @Bean
    open fun treasuryTransferIndexer(
        thorClient: ThorClient,
        processor: TreasuryTransferProcessor,
        @Value("\${indexer.start-block.b3tr-treasury}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr-treasury}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContractAddress: String,
        @Value("\${business-event.substitutions.GM_NFT_CONTRACT}") gmNftContractAddress: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.TREASURY_TRANSFER.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(
                listOf(
                    "B3TR_TreasuryTransferIn",
                    "B3TR_TreasuryTransferOut",
                    "B3TR_TreasuryGmUpgrade",
                )
            )
            .businessEventContracts(listOf(b3trContractAddress, gmNftContractAddress))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .build()
}
