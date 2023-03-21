package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import org.vechain.indexer.model.WrappedClause
import org.vechain.indexer.repos.ClauseRepo
import org.vechain.indexer.service.ThorService

@Component
class ClauseIndexer(private val thorService: ThorService, private val clauseRepo: ClauseRepo): Indexer() {

    override fun processBlock(blockNumber: Long) {
        logger.info("Processing clauses in block $blockNumber")
        val block = thorService.getBlock(blockNumber)
        if (block.transactions.isNotEmpty() && block.transactions.any { it.clauses.isNotEmpty() }) {
            val clauses = block.transactions.flatMap { tx ->
                tx.clauses.mapIndexed { idx, cl ->
                    WrappedClause(
                        block,
                        tx,
                        cl,
                        idx
                    )
                }
            }
            clauseRepo.saveAll(clauses)
        }
    }

    override fun getStartingBlock(): Long {
        val maxBlockNumber = clauseRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
        logger.info("Starting clause indexer from block $maxBlockNumber...")
        return maxBlockNumber
    }

    companion object {
        private val logger = LogManager.getLogger(ClauseIndexer::class.java)
    }
}