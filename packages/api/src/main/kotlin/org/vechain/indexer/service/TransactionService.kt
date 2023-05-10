package org.vechain.indexer.service

import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Transaction
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.utils.HexUtil

@Service
open class TransactionService(private val transactionRepository: TransactionRepo) {

    open fun findById(id: String): Transaction? {
        return transactionRepository.findByIdOrNull(HexUtil.normalise(id))
    }

    open fun findByOrigin(
        address: String,
        includeDelegated: Boolean,
        pageable: Pageable
    ): List<Transaction> {
        val normalisedAddress = HexUtil.normalise(address)
        return if (includeDelegated) transactionRepository.findByOriginOrGasPayer(
            normalisedAddress,
            normalisedAddress,
            pageable
        )
        else transactionRepository.findByOrigin(normalisedAddress, pageable)
    }

    open fun findAllDelegated(delegator: String, pageable: Pageable): List<Transaction> {
        val normalisedAddress = HexUtil.normalise(delegator)
        return transactionRepository.findByOriginNotAndGasPayer(normalisedAddress, normalisedAddress, pageable)
    }

}