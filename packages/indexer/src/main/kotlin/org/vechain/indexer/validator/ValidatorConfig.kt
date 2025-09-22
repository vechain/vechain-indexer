package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("validator")
open class ValidatorConfig {
    @Bean
    open fun validatorArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<Validator, ValidatorArchive> =
        ArchiveService(
            mongoTemplate,
            Validator::class.java,
            ValidatorArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun validatorPruner(
        validatorArchiveService: ArchiveService<Validator, ValidatorArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): Pruner =
        PrunerService(ValidatorArchive::class, validatorArchiveService, prunerRemovalChunkSize)

    @Bean
    open fun validatorIndexer(
        thorClient: ThorClient,
        processor: ValidatorProcessor,
        @Value("\${indexer.start-block.validator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval.validator}") syncLogInterval: Long,
        @Value("\${indexer.channel-batch-size}") channelBatchSize: Int,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
        builtinStakerAddress: String,
        @Value("\${business-event.substitutions.STARGATE_STAKER_CONTRACT}")
        stargateStakerAddress: String,
    ): Indexer =
        IndexerFactory()
            .name("ValidatorIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .channelBatchSize(channelBatchSize)
            .includeFullBlock()
            .abis("abis/stargate")
            .abiContracts(listOf(builtinStakerAddress, stargateStakerAddress))
            .abiEventNames(listOf("DelegationInitiated", "DelegationAdded", "DelegationWithdrawn"))
            .excludeVetTransfers()
            .build()
}
