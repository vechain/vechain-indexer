package org.vechain.indexer

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.repository.CrudRepository

interface BaseIndexedRepository<T : IndexedDocument, ID> : CrudRepository<T, ID> {

    @Aggregation(pipeline = ["{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }"])
    fun getLatestRecord(): T?

    fun deleteAllByBlockNumberGreaterThanEqual(start: Long)
}
