package org.vechain.indexer.service

import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Transaction
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.utils.HexUtil

@Service
open class TransactionService(private val transactionRepository: TransactionRepo) {

    open fun findByOrigin(
        origin: String,
        includeDelegated: Boolean?,
        pageable: Pageable
    ): List<Transaction> {
        val normalisedOrigin = HexUtil.normalise(origin)
        return if (includeDelegated == true) transactionRepository.findAllByOriginOrGasPayer(normalisedOrigin, pageable)
            .toList()
        else transactionRepository.findAllByOrigin(normalisedOrigin, pageable).toList()
    }

    open fun findAllDelegated(delegator: String, pageable: Pageable): List<Transaction> {
        return transactionRepository.findAllDelegated(HexUtil.normalise(delegator), pageable).toList()
    }

}