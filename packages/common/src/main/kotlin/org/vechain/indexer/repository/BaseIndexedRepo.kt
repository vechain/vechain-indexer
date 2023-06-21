package org.vechain.indexer.repository

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.repository.CrudRepository
import org.vechain.indexer.model.IndexedDocument

interface BaseIndexedRepo<T : IndexedDocument> : CrudRepository<T, String> {

    @Aggregation(
        pipeline = [
            "{ '\$sort': { 'blockNumber': -1 } }",
            "{ '\$limit': 1 }",
        ]
    )
    fun getLatestRecord(): T?

    fun deleteAllByBlockNumberBetween(start: Long, end: Long)

    fun findAllByBlockNumber(blockNumber: Long): List<T>
}