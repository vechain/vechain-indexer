package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService

@Profile("stargate", "vtho-claimed-by-account")
@Component
open class VthoClaimedByAccountProcessor(
    private val service: VthoClaimedByAccountService,
    vthoClaimByAccountArchiveService:
        ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive>,
    repository: VthoClaimedByAccountRepository,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = vthoClaimByAccountArchiveService,
    ) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Find any existing records
        val existing = service.getExisting(entry.events())

        // Process the updated records
        val updated = service.parseRecords(entry.events(), existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            service.save(updated, existing)
        }
    }
}
