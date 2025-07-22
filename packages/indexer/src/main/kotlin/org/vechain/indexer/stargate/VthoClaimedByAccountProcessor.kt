package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.stargate.VthoClaimedByAccount
import org.vechain.indexer.model.stargate.VthoClaimedByAccountArchive
import org.vechain.indexer.repository.stargate.VthoClaimedByAccountRepository
import org.vechain.indexer.thor.model.Block

@Profile("stargate")
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

    override fun process(events: List<IndexedEvent>, block: Block?) {
        if (events.isEmpty()) {
            return
        }

        // Find any existing records
        val existing = service.getExisting(events)

        // Process the updated records
        val updated = service.parseRecords(events, existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            service.update(updated, existing)
        }
    }
}
