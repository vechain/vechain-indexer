package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.thor.client.ThorClient

/**
 * Wires the validator-block indexer.
 *
 * Reads validator state (signer's delegation flag, missed-slot attribution, online recovery) from
 * `ValidatorV2` — no V1 aggregator dependency. The single `callDataClause` here fetches the builtin
 * Energy contract's `totalSupply()`, which is the only chain read this indexer needs (per-block
 * reward = `supply[N] − supply[N−1]`).
 *
 * Activating the `validator-reward` profile now requires `validator-v2` to be active as well — the
 * `dependsOn(validatorV2Indexer)` declaration enforces both ordering and bean availability.
 */
@Configuration
@Profile("validator-reward")
open class ValidatorBlockConfig {
    @Bean
    open fun validatorBlockIndexer(
        thorClient: ThorClient,
        processor: ValidatorBlockProcessor,
        @Qualifier("validatorV2Indexer") validatorV2Indexer: Indexer,
        @Value("\${indexer.start-block.validator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.VALIDATOR_BLOCK.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .callDataClauses(listOf(TokenRewardService.energyTotalSupplyClause()))
            .includeFullBlock()
            .dependsOn(validatorV2Indexer)
            .build()
}
