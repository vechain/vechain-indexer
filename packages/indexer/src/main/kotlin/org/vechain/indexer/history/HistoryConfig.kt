package org.vechain.indexer.history

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerFactory
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.utils.FileUtils

@Configuration
@Profile("history")
open class HistoryConfig() {

    @Bean
    open fun historyIndexer(
        thorClient: ThorClient,
        processor: HistoryProcessor,
        @Value("\${indexer.startBlock.history}") startBlock: Long,
        @Value("\${indexer.syncLogInterval.history}") syncLogInterval: Long,
        bEProperties: BusinessEventProperties,
    ): Indexer {
        val abiFiles = FileUtils.getJsonFilePaths("abis", 2)
        return IndexerFactory()
            .name("HistoryIndexer")
            .thorClient(thorClient)
            .processor(processor)
            .abiFiles(abiFiles)
            .abiEventNames(listOf("Transfer", "TransferSingle", "TransferBatch"))
            .businessEventFiles(FileUtils.getJsonFilePaths("business-events", 2))
            .businessEventAbiFiles(abiFiles)
            .businessEventSubstitutionParams(bEProperties.substitutions)
            .startBlock(startBlock)
            .syncLoggerInterval(syncLogInterval)
            .includeFullBlock()
            .build()
    }
}
