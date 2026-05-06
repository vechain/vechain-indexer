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
open class B3trUserChallengesConfig {
    @Bean
    open fun b3trUserChallengesIndexer(
        thorClient: ThorClient,
        processor: B3trUserChallengesProcessor,
        @Value("\${indexer.start-block.b3tr-user-challenges}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${business-event.substitutions.CHALLENGES_CONTRACT}")
        challengesContractAddress: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.B3TR_USER_CHALLENGES.NAME)
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .abis("abis/b3tr")
            .abiEventNames(
                listOf(
                    "ChallengeCreated",
                    "ChallengeInviteAdded",
                    "ChallengeJoined",
                    "ChallengeLeft",
                    "ChallengeDeclined",
                    "ChallengeCompleted",
                    "ChallengePayoutClaimed",
                    "SplitWinPrizeClaimed",
                    "SplitWinCreatorRefunded",
                    "ChallengeRefundClaimed",
                )
            )
            .abiContracts(listOf(challengesContractAddress))
            .excludeVetTransfers()
            .build()
}
