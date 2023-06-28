package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedTransaction
import org.vechain.indexer.repository.TransactionRepository
import org.vechain.indexer.utils.HexUtils

@Profile("transactions")
@Service
open class TransactionService(
    private val transactionRepository: TransactionRepository,
) {

    open fun findById(id: String): IndexedTransaction? {
        return transactionRepository.findByIdOrNull(HexUtils.normalise(id))
    }

    open fun findByOrigin(
        address: Address,
        includeDelegated: Boolean,
        pageable: Pageable
    ): Page<IndexedTransaction> {
        return if (includeDelegated) {
            transactionRepository.findByOriginOrGasPayer(address.value, pageable)
        } else {
            transactionRepository.findByOrigin(address.value, pageable)
        }
    }

    open fun findAllDelegated(delegator: Address, pageable: Pageable): Page<IndexedTransaction> {
        return transactionRepository.findDelegated(delegator.value, pageable)
    }
}
