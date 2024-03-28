package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedClause

@Profile("clauses")
@Repository
interface ClauseRepository : BaseIndexedRepository<IndexedClause> {

    fun findByOriginOrTo(origin: String, to: String, pageable: Pageable): Slice<IndexedClause>
}
