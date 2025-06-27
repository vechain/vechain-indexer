package org.vechain.indexer.repository

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.repository.CrudRepository
import org.vechain.indexer.model.IndexedDocument

interface BaseIndexedRepository<T : IndexedDocument, ID> : CrudRepository<T, ID> {

    @Aggregation(pipeline = ["{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }"])
    fun getLatestRecord(): T?

    fun deleteAllByBlockNumberBetween(start: Long, end: Long)
}
