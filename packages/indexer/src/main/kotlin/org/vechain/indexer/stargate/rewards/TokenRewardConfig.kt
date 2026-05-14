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
 * Reads validator cycle state from `ValidatorV2` and the active-delegation set from `DelegationV2`
 * — no V1 aggregator dependency. The single `callDataClause` here fetches the builtin Energy
 * contract's `totalSupply()`, which is the only chain read this indexer needs (the per-block reward
 * is the delta between consecutive totals).
 *
 * The `dependsOn(delegationV2Indexer)` chain transitively pulls `validatorV2Indexer` in too:
 * `validator-v2 → delegation-v2 → token-reward`. So activating the `token-reward` profile requires
 * both upstream profiles to be active as well.
 */
@Configuration
@Profile("token-reward")
open class TokenRewardConfig {

    @Bean
    open fun tokenRewardIndexer(
        thorClient: ThorClient,
        processor: TokenRewardProcessor,
        @Qualifier("delegationV2Indexer") delegationV2Indexer: Indexer,
        @Value("\${indexer.start-block.delegation}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.TOKEN_REWARD.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .callDataClauses(listOf(TokenRewardService.energyTotalSupplyClause()))
            .includeFullBlock()
            .dependsOn(delegationV2Indexer)
            .build()
}
