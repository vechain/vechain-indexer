package org.vechain.indexer.stargate.rewards

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.validator.domain.ValidatorDecoder

@Configuration
@Profile("token-reward")
open class TokenRewardConfig {

    @Bean
    open fun tokenRewardIndexer(
        thorClient: ThorClient,
        processor: TokenRewardProcessor,
        @Qualifier("delegationIndexer") delegationIndexer: Indexer,
        @Value("\${indexer.start-block.delegation}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        bEProperties: BusinessEventProperties,
        @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
        getAllValidatorsAddress: String,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.TOKEN_REWARD.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .callDataClauses(ValidatorDecoder.buildClauses(getAllValidatorsAddress))
            .includeFullBlock()
            .dependsOn(delegationIndexer)
            .build()
}
