package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.validator.domain.ValidatorDecoder.buildClauses

@Configuration
@Profile("validator", "validator-stats")
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
    ): TargetedPruner<Validator, ValidatorArchive> =
        PrunerService(ValidatorArchive::class, validatorArchiveService, prunerRemovalChunkSize)

    @Bean
    open fun validatorIndexer(
        thorClient: ThorClient,
        processor: ValidatorProcessor,
        service: ValidatorService,
        validatorPruner: TargetedPruner<Validator, ValidatorArchive>,
        @Value("\${indexer.start-block.validator}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLogInterval: Long,
        @Value("\${indexer.channel-batch-size}") channelBatchSize: Int,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
        builtinStakerAddress: String,
        @Value("\${business-event.substitutions.GET_ALL_VALIDATORS_CONTRACT}")
        getAllValidatorsAddress: String,
    ): Indexer =
        IndexerFactory()
            .name(IndexerNames.VALIDATOR)
            .thorClient(thorClient)
            .processor(processor)
            .pruner(validatorPruner)
            .prunerInterval(prunerInterval)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .includeFullBlock()
            .abis("abis/stargate")
            .abiContracts(listOf(builtinStakerAddress))
            .abiEventNames(listOf("BeneficiarySet", "StakeDecreased"))
            .callDataClauses(buildClauses(getAllValidatorsAddress))
            .excludeVetTransfers()
            .build()
}
