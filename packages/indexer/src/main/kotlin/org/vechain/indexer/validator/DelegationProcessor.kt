package org.vechain.indexer.validator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("validator", "delegation")
@Component
open class DelegationProcessor(
    repository: DelegationRepository,
    archiveService: ArchiveService<Delegation, DelegationArchive>,
    checkpointService: CheckpointService,
    private val service: DelegationService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = archiveService,
        indexerName = IndexerNames.DELEGATION.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.DELEGATION.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val (updated, existing) =
            service.processBlock(entry.block, entry.events(), entry.callResults)

        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
