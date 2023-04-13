package org.vechain.indexer.repos

import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.WrappedClause

@Repository
interface ClauseRepo : PagingAndSortingRepository<WrappedClause, String>, CrudRepository<WrappedClause, String> {

    @Aggregation(pipeline = ["{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }"])
    fun getMaxBlockNumber(): List<WrappedClause>
}
