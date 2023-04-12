package org.vechain.indexer.repos

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Block

@Repository
interface BlockRepo : PagingAndSortingRepository<Block, String>, CrudRepository<Block, String> {

    @Aggregation(pipeline = ["{ '\$sort': { 'number': -1 } }", "{ '\$limit': 1 }"])
    fun getMaxBlockNumber(): List<Block>
}