package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

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

    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }

        // Find any existing records
        val existing = service.getExisting(matchedEvents)

        // Process the updated records
        val updated = service.parseRecords(matchedEvents, existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            service.update(updated, existing)
        }
    }
}
