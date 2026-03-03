package org.vechain.indexer.stargate.nftHolders

import org.springframework.beans.factory.annotation.Qualifier
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
@Profile("stargate", "nft-holders-by-block")
open class NftHoldersByBlockConfig {

    @Bean
    open fun nftHoldersByBlockIndexer(
        thorClient: ThorClient,
        processor: NftHoldersByBlockProcessor,
        @Qualifier("nftOwnerBalanceIndexer") nftOwnerBalanceIndexer: Indexer,
        @Value("\${indexer.start-block.nft-holders-by-block}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.STARGATE_NFT_CONTRACT}")
        stargateNftContract: String,
        @Value("\${business-event.substitutions.STARGATE_DELEGATION_CONTRACT}")
        stargateDelegationContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.NFT_HOLDERS_BY_BLOCK.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .businessEvents("business-events/stargate", "abis/stargate")
            .businessEventNames(listOf("STARGATE_STAKE", "STARGATE_UNSTAKE"))
            .businessEventContracts(listOf(stargateNftContract, stargateDelegationContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .dependsOn(nftOwnerBalanceIndexer)
            .build()
}
