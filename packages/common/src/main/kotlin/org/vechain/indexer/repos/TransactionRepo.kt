package org.vechain.indexer.repos

import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.WrappedTransaction

@Repository
interface TransactionRepo : CrudRepository<WrappedTransaction, String> {

    @Aggregation(pipeline = ["{ '\$sort': { 'blockNumber': -1 } }", "{ '\$limit': 1 }"])
    fun getMaxBlockNumber(): List<WrappedTransaction>

    fun findAllByOrigin(origin: String): List<WrappedTransaction>


    @Query("{gasPayer: ?0, origin: { \$ne: ?0}}")
    fun findAllDelegated(gasPayer: String): List<WrappedTransaction>

    @Query("{\$or: [{origin: ?0}, {gasPayer: ?0}] }")
    fun findAllByOriginOrGasPayer(address: String): List<WrappedTransaction>
}