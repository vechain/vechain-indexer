package org.vechain.indexer.repos

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.WrappedClause

@Repository
interface ClauseRepo: CrudRepository<WrappedClause, String> {

    @Aggregation(pipeline = ["{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }" ])
    fun getMaxBlockNumber(): List<WrappedClause>
}