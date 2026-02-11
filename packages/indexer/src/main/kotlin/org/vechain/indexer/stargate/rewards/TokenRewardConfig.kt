package org.vechain.indexer.stargate.rewards

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.validator.domain.ValidatorDecoder

@Configuration
@Profile("token-reward")
open class TokenRewardConfig {
    @Bean
    open fun tokenRewardArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<TokenReward> =
        ArchiveService(mongoTemplate, TokenReward::class.java, recordLimit)

    @Bean
    open fun tokenRewardPruner(
        tokenRewardArchiveService: ArchiveService<TokenReward>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<TokenReward> =
        PrunerService(tokenRewardArchiveService, prunerRemovalChunkSize, "TokenRewardArchive")

    @Bean
    open fun tokenRewardIndexer(
        thorClient: ThorClient,
        processor: TokenRewardProcessor,
        @Qualifier("delegationIndexer") delegationIndexer: Indexer,
        @Value("\${indexer.start-block.delegation}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.stargate}") syncBlockBatchSize: Long,
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
            .blockBatchSize(syncBlockBatchSize)
            .callDataClauses(ValidatorDecoder.buildClauses(getAllValidatorsAddress))
            .includeFullBlock()
            .dependsOn(delegationIndexer)
            .build()
}
