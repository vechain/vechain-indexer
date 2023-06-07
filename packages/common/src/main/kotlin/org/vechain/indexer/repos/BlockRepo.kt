package org.vechain.indexer.repos

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedBlock

@Profile("blocks")
@Repository
interface BlockRepo : BaseIndexedRepo<IndexedBlock> {
    fun findByBlockNumber(blockNumber: Long): IndexedBlock?
    fun findTopByOrderByBlockNumberDesc(): IndexedBlock?

    @Aggregation(
        pipeline = [
            "{ '\$match': { 'isFinalized': false } }",
            "{ '\$sort': { 'blockNumber': 1 } }",
            "{ '\$limit': 1 }",
            "{ '\$project': { blockNumber: 1 } }"
        ]
    )
    fun getLowestUnfinalisedBlock(): Long?
}
