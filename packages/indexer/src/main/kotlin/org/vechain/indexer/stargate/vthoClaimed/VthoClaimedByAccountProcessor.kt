package org.vechain.indexer.stargate.vthoClaimed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService

@Profile("stargate", "vtho-claimed-by-account")
@Component
open class VthoClaimedByAccountProcessor(
    private val service: VthoClaimedByAccountService,
    vthoClaimByAccountArchiveService:
        ArchiveService<VthoClaimedByAccount, VthoClaimedByAccountArchive>,
    repository: VthoClaimedByAccountRepository,
    checkpointService: CheckpointService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = vthoClaimByAccountArchiveService,
        indexerName = IndexerNames.VTHO_CLAIMED_BY_ACCOUNT.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.VTHO_CLAIMED_BY_ACCOUNT.COLLECTION,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
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

            withContext(Dispatchers.IO) {
                service.save(
                    updatedAccountRecords.plus(updatedExistingAccountTokenIdRecords),
                    existingAccountRecords.plus(existingAccountTokenIdRecords),
                )
            }
        }
    }
}
