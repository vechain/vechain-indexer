package org.vechain.indexer.b3tr.challenges

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("b3tr", "b3tr-challenges")
open class B3trChallengesConfig {
    @Bean
    open fun b3trChallengesIndexer(
        thorClient: ThorClient,
        processor: B3trChallengesProcessor,
        @Value("\${indexer.start-block.b3tr-challenges}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr-challenges}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.CHALLENGES_CONTRACT}")
        challengesContractAddress: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.B3TR_CHALLENGES.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .abis("abis/b3tr")
            .abiEventNames(
                listOf(
                    "ChallengeCreated",
                    "SplitWinConfigured",
                    "ChallengeInviteAdded",
                    "ChallengeJoined",
                    "ChallengeLeft",
                    "ChallengeDeclined",
                    "ChallengeCancelled",
                    "ChallengeActivated",
                    "ChallengeInvalidated",
                    "ChallengeCompleted",
                    "ChallengePayoutClaimed",
                    "SplitWinPrizeClaimed",
                    "SplitWinCreatorRefunded",
                    "ChallengeRefundClaimed",
                )
            )
            .abiContracts(listOf(challengesContractAddress))
            .build()
}
