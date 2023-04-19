package org.vechain.indexer.repos

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.repository.CrudRepository
import org.vechain.indexer.model.IndexedDocument

interface BaseIndexedRepo<T : IndexedDocument> : CrudRepository<T, String> {

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