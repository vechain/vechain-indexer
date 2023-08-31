package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.repository.ClauseRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockUtils

@Profile("clauses")
@Component
open class ClauseIndexer(
    private val clauseRepository: ClauseRepository,
    private val mongoTemplate: MongoTemplate,
    thorClient: ThorClient,
    @Value("\${indexer.startBlock.clauses}") private val startBlock: Long,
    @Value("\${indexer.syncLoggerInterval.clauses}") private val syncLoggerInterval: Long,
) :
    VeWorldIndexer(
        repository = clauseRepository,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLoggerInterval = syncLoggerInterval
    ) {

    override fun rollback(blockNumber: Long) {
        clauseRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }

    override fun processBlock(block: Block) {
        val clauses: List<IndexedClause> = BlockUtils.getAllClauses(block)

        if (clauses.isNotEmpty()) mongoTemplate.insert(clauses, IndexedClause::class.java)
    }
}
