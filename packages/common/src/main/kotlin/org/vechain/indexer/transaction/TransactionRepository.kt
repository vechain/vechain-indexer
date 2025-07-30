package org.vechain.indexer.transaction

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository

@Profile("transactions")
@Repository
interface TransactionRepository :
    BasePagingAndSortingIndexedRepository<IndexedTransaction, String> {

    fun findByOrigin(origin: String, pageable: Pageable): Slice<IndexedTransaction>

    fun findByOriginOrGasPayer(
        origin: String,
        gasPayer: String,
        pageable: Pageable,
    ): Slice<IndexedTransaction>

    fun findByGasPayerAndOriginNot(
        gasPayer: String,
        origin: String,
        pageable: Pageable,
    ): Slice<IndexedTransaction>
}
