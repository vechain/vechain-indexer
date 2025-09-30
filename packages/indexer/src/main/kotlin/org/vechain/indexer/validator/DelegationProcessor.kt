package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService

@Profile("validator", "delegation")
@Component
open class DelegationProcessor(
    repository: DelegationRepository,
    archiveService: ArchiveService<Delegation, DelegationArchive>,
    private val service: DelegationService,
) : BaseStatefulProcessor(repository = repository, archiveService = archiveService) {
    override fun process(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val (updated, existing) =
            service.processBlock(entry.block, entry.events(), entry.callResults)

        if (updated.isNotEmpty()) {
            service.save(updated, existing)
        }
    }
}
