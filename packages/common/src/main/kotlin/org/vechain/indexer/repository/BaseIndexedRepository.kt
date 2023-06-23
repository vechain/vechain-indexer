package org.vechain.indexer.repository

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.vechain.indexer.model.IndexedDocument

interface BaseIndexedRepository<T : IndexedDocument> : CrudRepository<T, String>,
    PagingAndSortingRepository<T, String> {

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