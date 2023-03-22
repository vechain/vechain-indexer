package org.vechain.indexer

import org.springframework.stereotype.Component
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.service.ThorService

@Component
class BlockIndexer(private val thorService: ThorService, private val blockRepo: BlockRepo): Indexer() {
    override fun name() = "BlockIndexer"

    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)
        blockRepo.save(block)
    }

    override fun getStartingBlock(): Long {
        return blockRepo.getMaxBlockNumber().firstOrNull()?.number ?: 0
    }

}