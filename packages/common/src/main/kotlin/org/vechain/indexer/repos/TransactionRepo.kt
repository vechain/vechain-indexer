package org.vechain.indexer.repos

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Transaction

@Repository
interface TransactionRepo : BaseIndexedRepo<Transaction>, PagingAndSortingRepository<Transaction, String> {

    fun findAllByOrigin(origin: String, pageable: Pageable): Page<Transaction>

    @Query("{\$and: [{origin: {\$ne: ?0}}, {gasPayer: ?0}]}")
    fun findAllDelegated(gasPayer: String, pageable: Pageable): Page<Transaction>

    @Query("{\$or: [{origin: ?0}, {gasPayer: ?0}] }")
    fun findAllByOriginOrGasPayer(address: String, pageable: Pageable): Page<Transaction>

}