package org.vechain.indexer.b3tr.challenges

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
@Profile("b3tr", "b3tr-challenges")
open class B3trUserChallengesConfig {
    @Bean
    open fun b3trUserChallengesIndexer(
        thorClient: ThorClient,
        processor: B3trUserChallengesProcessor,
        @Value("\${indexer.start-block.b3tr-user-challenges}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.b3tr-user-challenges}") syncBlockBatchSize: Long,
        @Value("\${business-event.substitutions.CHALLENGES_CONTRACT}")
        challengesContractAddress: String,
        @Value("\${business-event.substitutions.EMISSIONS}") emissionsContractAddress: String,
        @Value("\${business-event.substitutions.B3TR_CONTRACT}") b3trContractAddress: String,
        @Value("\${business-event.substitutions.X2EARN_REWARDS_POOL_CONTRACT}")
        x2EarnRewardsPoolContract: String,
        bEProperties: BusinessEventProperties,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.B3TR_USER_CHALLENGES.NAME)
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
                    "MaxParticipantsUpdated",
                    "EmissionDistributed",
                    "EmissionDistributedV2",
                )
            )
            .abiContracts(listOf(challengesContractAddress, emissionsContractAddress))
            .businessEvents("business-events/b3tr", "abis/b3tr")
            .businessEventNames(listOf("B3TR_ActionReward"))
            .businessEventContracts(listOf(b3trContractAddress, x2EarnRewardsPoolContract))
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .excludeVetTransfers()
            .build()
}
