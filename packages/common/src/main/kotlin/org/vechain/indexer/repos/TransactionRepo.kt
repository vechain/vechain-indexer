package org.vechain.indexer.repos

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.WrappedTransaction

@Repository
interface TransactionRepo : PagingAndSortingRepository<WrappedTransaction, String>,
    CrudRepository<WrappedTransaction, String> {

    @Aggregation(pipeline = ["{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }"])
    fun getMaxBlockNumber(): List<WrappedTransaction>

    fun findAllByOrigin(origin: String, pageable: Pageable): Page<WrappedTransaction>

    @Query("{\$and: [{origin: {\$ne: ?0}}, {gasPayer: ?0}]}")
    fun findAllDelegated(gasPayer: String, pageable: Pageable): Page<WrappedTransaction>

    @Query("{\$or: [{origin: ?0}, {gasPayer: ?0}] }")
    fun findAllByOriginOrGasPayer(address: String, pageable: Pageable): Page<WrappedTransaction>
}