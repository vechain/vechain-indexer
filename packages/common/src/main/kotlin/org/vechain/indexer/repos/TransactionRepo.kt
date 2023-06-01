package org.vechain.indexer.repos

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedTransaction

@Profile("transactions")
@Repository
interface TransactionRepo : BaseIndexedRepo<IndexedTransaction>,
    PagingAndSortingRepository<IndexedTransaction, String> {

    fun findByOrigin(origin: String, pageable: Pageable): List<IndexedTransaction>

    fun findByOriginNotAndGasPayer(origin: String, gasPayer: String, pageable: Pageable): List<IndexedTransaction>

    fun findByOriginOrGasPayer(origin: String, gasPayer: String, pageable: Pageable): List<IndexedTransaction>

}
