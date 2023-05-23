package org.vechain.indexer.repos

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.WrappedClause

@Profile("clauses")
@Repository
interface ClauseRepo : BaseIndexedRepo<WrappedClause>, PagingAndSortingRepository<WrappedClause, String> {
    fun findByOriginOrTo(origin: String, to: String, pageable: Pageable): List<WrappedClause>
}
