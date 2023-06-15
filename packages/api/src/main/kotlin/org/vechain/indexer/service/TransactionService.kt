package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedTransaction
import org.vechain.indexer.repository.CountRepository
import org.vechain.indexer.repository.TransactionRepo
import org.vechain.indexer.utils.HexUtils

@Profile("transactions")
@Service
open class TransactionService(
    private val transactionRepository: TransactionRepo,
    private val countRepository: CountRepository
) {

    open fun findById(id: String): IndexedTransaction? {
        return transactionRepository.findByIdOrNull(HexUtils.normalise(id))
    }

    open fun findByOrigin(
        address: String,
        includeDelegated: Boolean,
        pageable: Pageable
    ): Page<IndexedTransaction> {
        val normalisedAddress = HexUtils.normalise(address)
        val slice: Slice<IndexedTransaction>
        val matchOperations = mutableListOf<MatchOperation>()

        if (includeDelegated) {
            slice = transactionRepository.findByOriginOrGasPayer(normalisedAddress, normalisedAddress, pageable)
            matchOperations.add(
                MatchOperation(
                    Criteria.where("").orOperator(
                        Criteria.where(ORIGIN).`is`(normalisedAddress),
                        Criteria.where(GAS_PAYER).`is`(normalisedAddress)
                    )
                )

            )
        } else {
            slice = transactionRepository.findByOrigin(normalisedAddress, pageable)
            matchOperations.add(MatchOperation(Criteria.where(ORIGIN).`is`(normalisedAddress)))
        }
        val count = countRepository.getCount(TRANSACTIONS_COLLECTION, matchOperations)

        return PageImpl(slice.content, pageable, count)
    }

    open fun findAllDelegated(delegator: String, pageable: Pageable): Page<IndexedTransaction> {
        val normalisedAddress = HexUtils.normalise(delegator)

        val slice = transactionRepository.findByOriginNotAndGasPayer(normalisedAddress, normalisedAddress, pageable)
        val count = countRepository.getCount(
            TRANSACTIONS_COLLECTION,
            listOf(
                MatchOperation(Criteria.where(ORIGIN).ne(normalisedAddress)),
                MatchOperation(Criteria.where(GAS_PAYER).`is`(normalisedAddress)),
            )
        )

        return PageImpl(slice.content, pageable, count)
    }

    companion object {
        val TRANSACTIONS_COLLECTION = IndexedTransaction::class.java
        val ORIGIN = IndexedTransaction::origin.name
        val GAS_PAYER = IndexedTransaction::gasPayer.name
    }

}
