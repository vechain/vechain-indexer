package org.vechain.indexer.vevote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("vevote", "vevote-results")
@Component
open class VeVoteResultProcessor(
    private val service: VeVoteResultService,
    repository: VeVoteProposalResultRepository,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VEVOTE_RESULT,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        // Process votes in the service
        val (updated, archives) = service.processEvents(entry.events())

        // Save the results
        if (updated.isNotEmpty() || archives.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, archives) }
        }
    }
}
