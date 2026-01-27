package org.vechain.indexer.vevote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("vevote", "vevote-comments")
@Component
open class VeVoteCommentProcessor(
    private val vevoteCommentRepository: VevoteCommentRepository,
    private val veVoteCommentService: VeVoteCommentService,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository = vevoteCommentRepository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VEVOTE_COMMENT,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        // Filter events to only those related to VeVote comments
        val allowedReason = veVoteCommentService.processComment(entry.events())

        // Save the results
        if (allowedReason.isNotEmpty()) {
            withContext(Dispatchers.IO) { vevoteCommentRepository.saveAll(allowedReason) }
        }
    }
}
