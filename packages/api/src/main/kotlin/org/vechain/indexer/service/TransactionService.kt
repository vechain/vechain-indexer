package org.vechain.indexer.service

import org.springframework.stereotype.Service
import org.vechain.indexer.model.WrappedTransaction
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.utils.HexUtil

@Service
open class TransactionService(private val transactionRepository: TransactionRepo) {

    open fun findByOrigin(origin: String): List<WrappedTransaction> {
        return transactionRepository.findAllByOrigin(HexUtil.normalise(origin))
    }

    open fun findAllDelegated(delegator: String): List<WrappedTransaction> {
        return transactionRepository.findAllDelegated(HexUtil.normalise(delegator))
    }

    open fun findByOriginOrGasPayer(address: String): List<WrappedTransaction> {
        return transactionRepository.findAllByOriginOrGasPayer(HexUtil.normalise(address))
    }

}