package org.vechain.indexer.b3tr.gm

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.repository.GmLevelOverviewRepository

@Profile("b3tr", "gm-nft-level-overview")
@Service
open class GmLevelOverviewService(
    private val repository: GmLevelOverviewRepository,
    private val gmLevelOverviewArchive: ArchiveService<GmLevelOverview, GmLevelOverviewArchive>,
    @param:Value("\${business-event.substitutions.GM_NFT_CONTRACT}")
    private val contractAddress: String,
) {

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<GmLevelOverview>, existing: List<GmLevelOverview>) {
        // Apply updates
        if (updated.isNotEmpty()) {
            repository.saveAll(updated)
        }

        // Apply archives
        if (existing.isNotEmpty()) {
            gmLevelOverviewArchive.saveAll(existing)
        }
    }
}
