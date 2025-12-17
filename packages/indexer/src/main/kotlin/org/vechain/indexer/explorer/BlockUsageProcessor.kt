package org.vechain.indexer.explorer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.explorer.repository.BlockUsageRepository
import org.vechain.indexer.version.IndexerVersionService

@Profile("explorer", "block-usage")
@Component
open class BlockUsageProcessor(
    repository: BlockUsageRepository,
    private val service: BlockUsageService,
    indexerVersionService: IndexerVersionService,
) :
    BaseProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.BLOCK_USAGE,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }
        val blockUsageRecord = service.processBlock(entry.block)

        withContext(Dispatchers.IO) { service.save(blockUsageRecord) }
    }
}
