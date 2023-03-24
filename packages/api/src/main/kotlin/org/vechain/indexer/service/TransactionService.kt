package org.vechain.indexer.service

import org.springframework.stereotype.Service
import org.vechain.indexer.model.Transaction
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.utils.HexUtil

@Service
class TransactionService(private val transactionRepository: TransactionRepo) {

    fun findByOrigin(origin: String): List<Transaction> {
        return transactionRepository.findAllByOrigin(HexUtil.normalise(origin))
    }

    fun findByDelegator(delegator: String): List<Transaction> {
        return transactionRepository.findAllByDelegator(HexUtil.normalise(delegator))
    }

    fun findByOriginOrDelegator(address: String): List<Transaction> {
        return transactionRepository.findAllByOriginOrDelegator(HexUtil.normalise(address))
    }

}