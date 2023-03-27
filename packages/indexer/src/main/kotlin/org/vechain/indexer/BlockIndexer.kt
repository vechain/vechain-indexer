package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.service.ThorService

@Profile("block-indexer", "prod")
@Component
open class BlockIndexer(private val thorService: ThorService, private val blockRepo: BlockRepo) : Indexer() {
    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)
        blockRepo.save(block)
    }

    override fun getStartingBlock(): Long {
        return blockRepo.getMaxBlockNumber().firstOrNull()?.number ?: 0
    }

}