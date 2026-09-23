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
import org.vechain.indexer.thor.client.ThorClient

/**
 * Wires the token-reward indexer.
 *
 * Reads validator cycle state from `validator.cycle`, and active delegations as of the block — no
 * V1 aggregator dependency. The service reads the signer's delegator pool from the chain itself.
 *
 * The `dependsOn(delegationIndexer)` chain transitively pulls `validatorIndexer` in too: `validator
 * → delegation-v2 → token-reward`. So activating the `token-reward` profile requires both upstream
 * profiles to be active as well.
 */
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
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.TOKEN_REWARD.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .includeFullBlock()
            .dependsOn(delegationIndexer)
            .build()
}
