package org.vechain.indexer.repos

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Transaction

@Profile("transactions")
@Repository
interface TransactionRepo : BaseIndexedRepo<Transaction>, PagingAndSortingRepository<Transaction, String> {

    fun findByOrigin(origin: String, pageable: Pageable): List<Transaction>

    fun findByOriginNotAndGasPayer(origin: String, gasPayer: String, pageable: Pageable): List<Transaction>

    fun findByOriginOrGasPayer(origin: String, gasPayer: String, pageable: Pageable): List<Transaction>

}
