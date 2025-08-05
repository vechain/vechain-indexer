package org.vechain.indexer.b3tr.sustainability.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.b3tr.sustainability.Action

interface CustomActionRepository {
    fun findActionsByFilters(
        appId: String?,
        wallet: String?,
        startTimestamp: Long?,
        endTimestamp: Long?,
        pageable: Pageable,
    ): Slice<Action>
}
