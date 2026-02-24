package org.vechain.indexer

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.repository.CrudRepository

interface BaseIndexedRepository<T : IndexedDocument, ID> : CrudRepository<T, ID> {

    @Aggregation(pipeline = ["{ '\$sort': { '_id': -1 } }", "{ '\$limit': 1 }"])
    fun getLatestRecord(): T?

    fun deleteAllByBlockNumberGreaterThanEqual(start: Long)

    @Query("{}") fun findAll(pageable: Pageable): Slice<T>
}
