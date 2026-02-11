package org.vechain.indexer

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.repository.CrudRepository

interface BaseIndexedRepository<T : IndexedDocument, ID> : CrudRepository<T, ID> {

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { '_id': { '\$ne': '__checkpoint__' } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    fun getLatestRecord(): T?

    @Query(
        value = "{ '_id': { '\$ne': '__checkpoint__' }, 'blockNumber': { '\$gte': ?0 } }",
        delete = true,
    )
    fun deleteAllByBlockNumberGreaterThanEqual(start: Long)
}
