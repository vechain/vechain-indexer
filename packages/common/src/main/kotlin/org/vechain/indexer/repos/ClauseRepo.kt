package org.vechain.indexer.repos

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.WrappedClause

@Repository
interface ClauseRepo : BaseIndexedRepo<WrappedClause> {

    /**
     * findAll() triggers collection count even without returning a page object.
     * findAllBy() is used to paginate results without counting all elements
     */
    fun findAllBy(pageable: Pageable): List<WrappedClause>

}
