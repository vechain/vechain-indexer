package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("vevote", "vevote-results")
@Component
open class VeVoteResultProcessor(
    private val service: VeVoteResultService,
    repository: VeVoteProposalResultRepository,
    veVoteResultArchiveService: ArchiveService<VeVoteProposalResult, VeVoteProposalResultArchive>,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = veVoteResultArchiveService,
        indexerName = IndexerNames.VEVOTE_RESULT.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.VEVOTE_RESULT.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        // Process votes in the service
        val (updated, archives) = service.processEvents(entry.events())

        // Save the results
        if (updated.isNotEmpty() || archives.isNotEmpty()) {
            service.save(updated, archives)
        }
    }
}
