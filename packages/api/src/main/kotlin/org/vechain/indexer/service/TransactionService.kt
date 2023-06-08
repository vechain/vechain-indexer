package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedTransaction
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.utils.HexUtils

@Profile("transactions")
@Service
open class TransactionService(private val transactionRepository: TransactionRepo) {

    open fun findById(id: String): IndexedTransaction? {
        return transactionRepository.findByIdOrNull(HexUtils.normalise(id))
    }

    open fun findByOrigin(
        address: String,
        includeDelegated: Boolean,
        pageable: Pageable
    ): List<IndexedTransaction> {
        val normalisedAddress = HexUtils.normalise(address)
        return if (includeDelegated) transactionRepository.findByOriginOrGasPayer(
            normalisedAddress,
            normalisedAddress,
            pageable
        )
        else transactionRepository.findByOrigin(normalisedAddress, pageable)
    }

    open fun findAllDelegated(delegator: String, pageable: Pageable): List<IndexedTransaction> {
        val normalisedAddress = HexUtils.normalise(delegator)
        return transactionRepository.findByOriginNotAndGasPayer(normalisedAddress, normalisedAddress, pageable)
    }

}
