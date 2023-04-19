package org.vechain.indexer.repos

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.vechain.indexer.model.IndexerDocument

interface IndexerRepo<T : IndexerDocument> : PagingAndSortingRepository<T, String>, CrudRepository<T, String> {

    @Aggregation(
        pipeline = [
            "{ '\$sort': { 'blockNumber': -1 } }",
            "{ '\$limit': 1 }",
            "{ '\$project': { blockNumber: 1 } }"
        ]
    )
    fun getMaxBlockNumber(): Long?

    @Aggregation(
        pipeline = [
            "{ '\$sort': { 'blockNumber': -1 } }",
            "{ '\$limit': 1 }",
            "{ '\$project': { blockId: 1 } }"
        ]
    )
    fun getMaxBlockId(): String?

    fun deleteAllByBlockNumberBetween(start: Long, end: Long)
}