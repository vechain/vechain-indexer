package org.vechain.indexer.repos

import org.springframework.data.domain.Page
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.WrappedClause

@Repository
interface ClauseRepo : BaseIndexedRepo<WrappedClause>, PagingAndSortingRepository<WrappedClause, String> {
    fun findByOriginOrTo(origin: String, to: String, pageable: Pageable): Page<WrappedClause>
}
