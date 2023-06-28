package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.repository.ClauseRepository
import org.vechain.indexer.utils.BlockUtils
import org.vechain.thor.model.Block

@Profile("clauses")
@Component
open class ClauseIndexer(
    private val clauseRepository: ClauseRepository,
    private val mongoTemplate: MongoTemplate,
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${indexer.startBlock.clauses}") private val startBlock: Long,
) :
    VeWorldIndexer(clauseRepository, startBlock, thorUrl) {

    override fun rollback(blockNumber: Long) {
        clauseRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }

    override fun processBlock(block: Block) {
        val clauses: List<IndexedClause> = BlockUtils.getAllClauses(block)

        if (clauses.isNotEmpty()) mongoTemplate.insert(clauses, IndexedClause::class.java)
    }

}
