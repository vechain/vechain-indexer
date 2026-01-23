package org.vechain.indexer.transaction

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.thor.model.BlockIdentifier

interface TransactionRepository {

    fun saveAll(transactions: List<IndexedTransaction>): List<IndexedTransaction>

    fun findById(id: String): IndexedTransaction?

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

    fun findByContractAddress(
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransaction>

    fun findByContractAddresses(
        contractAddresses: List<String>,
        pageable: Pageable,
    ): Slice<IndexedTransaction>

    fun getLatestBlockIdentifier(): BlockIdentifier?

    fun deleteAllByBlockNumberGreaterThanEqual(start: Long)
}
