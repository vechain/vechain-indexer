package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("history")
@Repository
interface HistoryRepository : BasePagingAndSortingIndexedRepository<IndexedHistoryEvent, String> {
    fun findByCriteria(criteria: Criteria, pageable: Pageable): Slice<IndexedHistoryEvent>
}
