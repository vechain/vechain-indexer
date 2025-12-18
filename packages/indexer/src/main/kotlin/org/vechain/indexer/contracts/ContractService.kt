package org.vechain.indexer.contracts

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.contracts.repository.ContractRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments

@Profile("contracts", "contract")
@Service
open class ContractService(
    private val repository: ContractRepository,
    private val archiveService: ArchiveService<Contract, ContractArchive>,
    private val contractPruner: TargetedPruner<Contract, ContractArchive>,
) {
    open fun processBlock(events: List<IndexedEvent>): Pair<List<Contract>, List<Contract>> {
        // Business logic intentionally not implemented (boilerplate only).
        return Pair(emptyList(), emptyList())
    }

    @Transactional
    open fun save(updated: List<Contract>, existing: List<Contract>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            repository = repository,
            archiveService = archiveService,
            pruner = contractPruner,
        )
    }

    protected fun resolveExisting(recordId: String, cache: Map<String, Contract>): Contract? =
        cache[recordId] ?: repository.findByIdOrNull(recordId)
}
