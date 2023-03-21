package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import org.vechain.indexer.model.WrappedTransaction
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.service.ThorService

@Component
class TransactionIndexer(private val thorService: ThorService, private val txRepo: TransactionRepo): Indexer() {

    override fun processBlock(blockNumber: Long) {
        logger.info("Processing transactions in block $blockNumber")
        val block = thorService.getBlock(blockNumber)
        if (block.transactions.isNotEmpty())
            txRepo.saveAll(block.transactions.map { WrappedTransaction(block, it) })
    }

    override fun getStartingBlock(): Long {
        val maxBlockNumber = txRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
        logger.info("Starting transaction indexer from block $maxBlockNumber...")
        return maxBlockNumber
    }

    companion object {
        private val logger = LogManager.getLogger(TransactionIndexer::class.java)
    }
}