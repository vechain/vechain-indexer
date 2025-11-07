package org.vechain.indexer.stargate.vthoClaimed

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "vtho-claimed-by-account")
@Component
open class VthoClaimedByAccountProcessor(
    private val service: VthoClaimedByAccountService,
    vthoClaimByAccountArchiveService:
        ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive>,
    repository: VthoClaimedByAccountRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = vthoClaimByAccountArchiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VTHO_CLAIMED_BY_ACCOUNT,
    ) {
    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Process account records

        // Find any existing records
        val existingAccountRecords = service.getExistingByAccount(entry.events())

        // Process the updated records
        val updatedAccountRecords =
            service.parseAccountRecords(entry.events(), existingAccountRecords)

        // Process account token id records
        val existingAccountTokenIdRecords = service.getExistingByAccountTokenId(entry.events())
        val updatedExistingAccountTokenIdRecords =
            service.parseAccountTokenIdRecords(entry.events(), existingAccountTokenIdRecords)

        // Finally save the updated records and archive the existing ones
        if (updatedAccountRecords.isNotEmpty() || existingAccountRecords.isNotEmpty()) {

            service.save(
                updatedAccountRecords.plus(updatedExistingAccountTokenIdRecords),
                existingAccountRecords.plus(existingAccountTokenIdRecords),
            )
        }
    }
}
