package org.vechain.indexer

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository

interface BasePagingAndSortingIndexedRepository<T : IndexedDocument, ID> :
    BaseIndexedRepository<T, ID>, PagingAndSortingRepository<T, ID> {

    @Query("{ '_id': { '\$ne': '__checkpoint__' } }")
    override fun findAll(pageable: Pageable): Page<T>
}
