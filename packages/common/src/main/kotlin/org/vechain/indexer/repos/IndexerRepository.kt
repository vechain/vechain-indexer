package org.vechain.indexer.repos

import org.springframework.data.mongodb.repository.Aggregation

data class BlockNumber(val blockNumber: Long)
interface IndexerRepository {

    @Aggregation(pipeline = ["{ '\$project': { _id: 0, blockNumber: 1 } }", "{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }"])
    fun getMaxBlockNumber(): BlockNumber?

    fun deleteAllByBlockNumberBetween(start: Long, end: Long)
}