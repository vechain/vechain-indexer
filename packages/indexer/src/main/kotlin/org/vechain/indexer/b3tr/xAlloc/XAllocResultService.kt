package org.vechain.indexer.b3tr.xAlloc

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.voting.repository.XAllocResultRepository
import org.vechain.indexer.event.model.generic.IndexedEvent

@Profile("b3tr", "b3tr-x-alloc")
@Service
open class XAllocResultService(
    private val repository: XAllocResultRepository,
    private val xAllocResultArchiveService: ArchiveService<XAllocResult, XAllocResultArchive>,
) {

    open fun processEvents(
        events: List<IndexedEvent>
    ): Pair<List<XAllocResult>, List<XAllocResult>> {
        error("Not implemented")
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<XAllocResult>, existing: List<XAllocResult>) {
        // Apply updates
        if (updated.isNotEmpty()) {
            repository.saveAll(updated)
        }

        // Apply archives
        if (existing.isNotEmpty()) {
            xAllocResultArchiveService.saveAll(existing)
        }
    }
}
