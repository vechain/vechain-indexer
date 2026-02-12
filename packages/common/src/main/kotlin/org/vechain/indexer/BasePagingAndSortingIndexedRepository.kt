package org.vechain.indexer

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query

interface BasePagingAndSortingIndexedRepository<T : IndexedDocument, ID> :
    BaseIndexedRepository<T, ID> {

    @Query("{ '_id': { '\$ne': '__checkpoint__' } }") fun findAll(pageable: Pageable): Slice<T>
}
