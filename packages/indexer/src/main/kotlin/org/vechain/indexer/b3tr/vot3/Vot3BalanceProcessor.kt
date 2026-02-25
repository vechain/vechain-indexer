package org.vechain.indexer.b3tr.vot3

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.vot3.repository.Vot3BalanceRepository
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("b3tr", "vot3-balance")
@Component
open class Vot3BalanceProcessor(
    private val service: Vot3BalanceService,
    repository: Vot3BalanceRepository,
    archiveService: ArchiveService<Vot3Balance, Vot3BalanceArchive>,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = archiveService,
        indexerName = IndexerNames.VOT3_BALANCE.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.VOT3_BALANCE.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val (updated, existing) = service.processBlock(entry.block, entry.events)

        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            service.save(updated, existing)
        }
    }
}
