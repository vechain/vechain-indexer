package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.WrappedClause
import org.vechain.indexer.repos.ClauseRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils

@Profile("clause-indexer", "prod")
@Component
open class ClauseIndexer(thorService: ThorService, private val clauseRepo: ClauseRepo) :
    Indexer(thorService, clauseRepo) {
    override fun processBlock(block: Block) {
        val clauses: List<WrappedClause> = BlockUtils.getAllClauses(block)

        if (clauses.isNotEmpty()) clauseRepo.saveAll(clauses)
    }

}