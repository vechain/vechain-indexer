package org.vechain.indexer.b3tr.action

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PostgresPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryConfig {
    @Bean
    open fun userRoundActionSummaryPruner(
        jdbcTemplate: JdbcTemplate,
        namedJdbcTemplate: NamedParameterJdbcTemplate,
        @Value("\${indexer.pruner.prune-block-depth:10000}") pruneBlockDepth: Long,
    ): PostgresPruner =
        PostgresPruner(
            jdbcTemplate,
            namedJdbcTemplate,
            pruneBlockDepth,
            "b3tr_user_action_summaries_round",
        )

    @Bean
    open fun userRoundActionSummaryIndexer(
        thorClient: ThorClient,
        processor: UserRoundActionSummaryProcessor,
        userRoundActionSummaryPruner: PostgresPruner,
        @Value("\${indexer.start-block.b3tr-sustainable-actions}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr}") syncBlockBatchSize: Long,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContract: String,
        @Value("\${business-event.substitutions.X2EARN_REWARDS_POOL_CONTRACT}")
        x2earnRewardsPoolContract: String,
        @Value("\${business-event.substitutions.EMISSIONS}") emissionsContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.USER_ROUND_ACTION_SUMMARY)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(userRoundActionSummaryPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/b3tr")
            .abiEventNames(listOf("EmissionDistributed", "EmissionDistributedV2"))
            .abiContracts(listOf(emissionsContract))
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(listOf("B3TR_ActionReward"))
            .businessEventContracts(listOf(b3trContract, x2earnRewardsPoolContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .excludeVetTransfers()
            .build()
}
