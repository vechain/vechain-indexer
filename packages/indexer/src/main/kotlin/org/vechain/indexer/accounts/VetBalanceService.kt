package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.repository.VetBalanceRepository
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.event.model.generic.IndexedEvent

@Profile("accounts", "vet-balance")
@Service
open class VetBalanceService(private val repository: VetBalanceRepository) {
    open fun processEvents(events: List<IndexedEvent>): List<VetBalance> {
        assertEventTypes(events, "VET_TRANSFER")
        TODO("Implement the business logic")
        return emptyList()
    }

    @Transactional
    open fun save(records: List<VetBalance>) {
        if (records.isEmpty()) return
        repository.saveAll(records)
    }
}
