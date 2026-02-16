package org.vechain.indexer.contracts

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.PrunerService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.client.ThorClient

@Configuration
@Profile("contracts", "contract")
open class ContractConfig {
    @Bean
    open fun contractArchiveService(
        mongoTemplate: MongoTemplate,
        @Value("\${indexer.pruner.record-limit}") recordLimit: Long,
    ): ArchiveService<Contract> = ArchiveService(mongoTemplate, Contract::class.java, recordLimit)

    @Bean
    open fun contractPruner(
        contractArchiveService: ArchiveService<Contract>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<Contract> = PrunerService(contractArchiveService, prunerRemovalChunkSize)

    @Bean
    open fun contractIndexer(
        thorClient: ThorClient,
        processor: ContractProcessor,
        contractPruner: TargetedPruner<Contract>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.contracts:500}") syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.CONTRACTS.NAME)
            .thorClient(thorClient)
            .pruner(contractPruner)
            .prunerInterval(prunerInterval)
            .processor(processor)
            .abis("abis/contract")
            .abiEventNames(listOf("\$Master"))
            .startBlock(0L)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .build()
}
