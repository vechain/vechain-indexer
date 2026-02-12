package org.vechain.indexer.transaction

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("transactions")
@Repository
interface TransactionRepository : BaseIndexedRepository<IndexedTransaction, String> {

    @Query("{ 'origin': ?0 }")
    fun findByOrigin(origin: String, pageable: Pageable): Slice<IndexedTransaction>

    @Query("{ '\$or': [{ 'origin': ?0 }, { 'gasPayer': ?1 }] }")
    fun findByOriginOrGasPayer(
        origin: String,
        gasPayer: String,
        pageable: Pageable,
    ): Slice<IndexedTransaction>

    @Query("{ 'gasPayer': ?0, 'origin': { '\$ne': ?1 } }")
    fun findByGasPayerAndOriginNot(
        gasPayer: String,
        origin: String,
        pageable: Pageable,
    ): Slice<IndexedTransaction>

    @Query("{ 'clauses.to': ?0 }")
    fun findByContractAddress(
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedTransaction>

    @Query("{ 'clauses.to': { \$in: ?0 } }")
    fun findByContractAddresses(
        contractAddresses: List<String>,
        pageable: Pageable,
    ): Slice<IndexedTransaction>
}
