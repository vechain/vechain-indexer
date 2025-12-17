package org.vechain.indexer.stargate.vthoGenerated

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "vtho-generated-by-block")
@Component
open class VthoGeneratedByBlockProcessor(
    private val service: VthoGeneratedByBlockService,
    repository: VthoGeneratedByBlockRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VTHO_GENERATED_BY_BLOCK,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val newRecord = service.processBlock(entry.events(), entry.block, entry.callResults())

        if (newRecord.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(newRecord) }
        }
    }
}
