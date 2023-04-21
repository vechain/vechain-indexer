package org.vechain.indexer.service

import org.springframework.data.domain.Page
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
        origin: String,
        includeDelegated: Boolean?,
        pageable: Pageable
    ): Page<Transaction> {
        val normalisedOrigin = HexUtil.normalise(origin)
        return if (includeDelegated == true) transactionRepository.findAllByOriginOrGasPayer(normalisedOrigin, pageable)
        else transactionRepository.findAllByOrigin(normalisedOrigin, pageable)
    }

    open fun findAllDelegated(delegator: String, pageable: Pageable): Page<Transaction> {
        return transactionRepository.findAllDelegated(HexUtil.normalise(delegator), pageable)
    }

}