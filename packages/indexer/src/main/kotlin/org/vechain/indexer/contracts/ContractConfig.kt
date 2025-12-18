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
    ): ArchiveService<Contract, ContractArchive> =
        ArchiveService(
            mongoTemplate,
            Contract::class.java,
            ContractArchive::class.java,
            recordLimit,
        )

    @Bean
    open fun contractPruner(
        contractArchiveService: ArchiveService<Contract, ContractArchive>,
        @Value("\${indexer.pruner.removal-chunk-size}") prunerRemovalChunkSize: Int,
    ): TargetedPruner<Contract, ContractArchive> =
        PrunerService(ContractArchive::class, contractArchiveService, prunerRemovalChunkSize)

    @Bean
    open fun contractIndexer(
        thorClient: ThorClient,
        processor: ContractProcessor,
        contractPruner: TargetedPruner<Contract, ContractArchive>,
        @Value("\${indexer.pruner.interval}") prunerInterval: Long,
        @Value("\${indexer.start-block.contracts:0}") startBlock: Long,
        @Value("\${indexer.sync-log-interval}") syncLoggerInterval: Long,
        @Value("\${indexer.sync-block-batch-size.contracts:500}") syncBlockBatchSize: Long,
    ): BlockIndexer =
        IndexerFactory()
            .name(IndexerNames.CONTRACTS_INDEXER)
            .thorClient(thorClient)
            .pruner(contractPruner)
            .prunerInterval(prunerInterval)
            .processor(processor)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLoggerInterval)
            .blockBatchSize(syncBlockBatchSize)
            .build()
}
