package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.WrappedClause
import org.vechain.indexer.repos.ClauseRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils

@Profile("clauses")
@Component
open class ClauseIndexer(
    thorService: ThorService,
    clauseRepo: ClauseRepo,
    mongoTemplate: MongoTemplate
) :
    Indexer(thorService, clauseRepo, mongoTemplate) {
    override fun processBlock(block: Block) {
        val clauses: List<WrappedClause> = BlockUtils.getAllClauses(block)

        if (clauses.isNotEmpty()) insertAll(clauses, WrappedClause::class.java)
    }

}
