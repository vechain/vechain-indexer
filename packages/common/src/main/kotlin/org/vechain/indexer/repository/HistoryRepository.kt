package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.*

@Profile("history")
@Repository
interface HistoryRepository : BasePagingAndSortingIndexedRepository<IndexedHistoryEvent, String> {}
