package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedActivity

@Profile("activities")
@Repository
interface ActivityRepository : BaseIndexedRepository<IndexedActivity> {
    fun findByAccount(account: String, pageable: Pageable): Slice<IndexedActivity>
}
