package org.vechain.indexer.repos

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Block

@Repository
interface BlockRepo : IndexerRepository, CrudRepository<Block, String> {
    fun findByBlockNumber(blockNumber: Long): Block?
    fun findTopByOrderByBlockNumberDesc(): Block?
}