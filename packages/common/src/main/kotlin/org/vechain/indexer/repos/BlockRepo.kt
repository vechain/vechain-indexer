package org.vechain.indexer.repos

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.Block

@Profile("blocks")
@Repository
interface BlockRepo : BaseIndexedRepo<Block> {
    fun findByBlockNumber(blockNumber: Long): Block?
    fun findTopByOrderByBlockNumberDesc(): Block?
}
