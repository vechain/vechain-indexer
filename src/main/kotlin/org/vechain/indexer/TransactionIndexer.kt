package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import org.vechain.indexer.model.WrappedTransaction
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.service.ThorService

@Component
class TransactionIndexer(private val thorService: ThorService, private val txRepo: TransactionRepo) {
    private var currentBlockNumber: Long = 0

    fun run() {

        try {
            logger.info("Starting transaction indexer...")
            initialise()
            while (true) {
                logger.info("Indexing transactions in block $currentBlockNumber")
                val block = thorService.getBlock(currentBlockNumber++)
                txRepo.saveAll(block.transactions.map { WrappedTransaction(block, it) })
            }
        } catch (e: Exception) {
            logger.error("Error while indexing transactions", e)
            logger.info("Restarting transaction indexer in 10s...")
            Thread.sleep(10000)
            run()
        }
    }

    private fun initialise() {
        val maxBlockNumber = txRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
        currentBlockNumber = maxBlockNumber
        logger.info("Starting transaction indexer from block $maxBlockNumber...")
    }

    companion object {
        private val logger = LogManager.getLogger(TransactionIndexer::class.java)
    }
}