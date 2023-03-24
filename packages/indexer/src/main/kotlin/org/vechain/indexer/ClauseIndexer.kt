package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.WrappedClause
import org.vechain.indexer.repos.ClauseRepo
import org.vechain.indexer.service.ThorService

@Profile("clause-indexer", "prod")
@Component
open class ClauseIndexer(private val thorService: ThorService, private val clauseRepo: ClauseRepo) : Indexer() {
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