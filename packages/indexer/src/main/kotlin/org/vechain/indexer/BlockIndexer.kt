package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.service.ThorService

@Profile("block-indexer", "prod")
@Component
open class BlockIndexer(private val thorService: ThorService, private val blockRepo: BlockRepo) : Indexer(thorService) {
    override fun processBlock(block: Block) {
        blockRepo.save(block)
    }

    override fun getStartingBlock(): Long {
        return blockRepo.getMaxBlockNumber().firstOrNull()?.number ?: 0
    }

}