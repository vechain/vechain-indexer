package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import org.vechain.indexer.model.WrappedClause
import org.vechain.indexer.repos.ClauseRepo
import org.vechain.indexer.service.ThorService

@Component
class ClauseIndexer(private val thorService: ThorService, private val clauseRepo: ClauseRepo) {
    private var currentBlockNumber: Long = 0

    fun run() {

        try {
            logger.info("Starting clause indexer...")
            initialise()
            while (true) {
                logger.info("Indexing clauses in block $currentBlockNumber")
                val block = thorService.getBlock(currentBlockNumber)
                val clauses = block.transactions.flatMap { tx -> tx.clauses.mapIndexed { idx, cl -> WrappedClause(block, tx, cl, idx) } }
                clauseRepo.saveAll(clauses)
                currentBlockNumber++
            }
        } catch (e: Exception) {
            logger.error("Error while indexing clauses for block $currentBlockNumber", e)
            logger.info("Restarting clause indexer in 10s...")
            Thread.sleep(10000)
            run()
        }
    }

    private fun initialise() {
        val maxBlockNumber = clauseRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
        currentBlockNumber = maxBlockNumber
        logger.info("Starting clause indexer from block $maxBlockNumber...")
    }

    companion object {
        private val logger = LogManager.getLogger(ClauseIndexer::class.java)
    }
}