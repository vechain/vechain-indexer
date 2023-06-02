package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.repos.ClauseRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils
import org.vechain.thor.model.Block

@Profile("clauses")
@Component
open class ClauseIndexer(
    thorService: ThorService,
    clauseRepo: ClauseRepo,
    private val mongoTemplate: MongoTemplate
) :
    VeWorldIndexer(thorService, clauseRepo) {
    override fun processBlock(block: Block) {
        val clauses: List<IndexedClause> = BlockUtils.getAllClauses(block)

        if (clauses.isNotEmpty()) mongoTemplate.insert(clauses, IndexedClause::class.java)
    }

}
