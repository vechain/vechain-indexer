package org.vechain.indexer

import org.springframework.stereotype.Component
import org.vechain.indexer.model.WrappedClause
import org.vechain.indexer.repos.ClauseRepo
import org.vechain.indexer.service.ThorService

@Component
class ClauseIndexer(private val thorService: ThorService, private val clauseRepo: ClauseRepo): Indexer() {
    override fun name() = "ClauseIndexer"

    override fun processBlock(blockNumber: Long) {
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
        return clauseRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
    }
}