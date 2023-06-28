package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedTransaction

@Profile("transactions")
@Repository
interface TransactionRepository : BaseIndexedRepository<IndexedTransaction> {

    fun findByOrigin(origin: String, pageable: Pageable): Page<IndexedTransaction>

    fun findByOriginOrGasPayer(address: String, pageable: Pageable): Page<IndexedTransaction>

    fun findDelegated(address: String, pageable: Pageable): Page<IndexedTransaction>
}
