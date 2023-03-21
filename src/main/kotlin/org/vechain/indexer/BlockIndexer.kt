package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.service.ThorService

@Component
class BlockIndexer(private val thorService: ThorService, private val blockRepo: BlockRepo): Indexer() {

    override fun processBlock(blockNumber: Long) {
        logger.info("Processing block $blockNumber")
        val block = thorService.getBlock(blockNumber)
        blockRepo.save(block)
    }

    override fun getStartingBlock(): Long {
        val maxBlockNumber = blockRepo.getMaxBlockNumber().firstOrNull()?.number ?: 0
        logger.info("Starting block indexer from block $maxBlockNumber...")
        return maxBlockNumber
    }

    companion object {
        private val logger = LogManager.getLogger(BlockIndexer::class.java)
    }
}