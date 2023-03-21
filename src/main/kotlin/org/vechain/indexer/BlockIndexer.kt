package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.springframework.stereotype.Component
import org.vechain.indexer.repos.BlockRepo
import org.vechain.indexer.service.ThorService


@Component
class BlockIndexer(private val thorService: ThorService, private val blockRepo: BlockRepo) {
    private var currentBlockNumber: Long = 0

    fun run() {
        try {
            logger.info("Starting block indexer...")
            initialise()
            while (true) {
                logger.info("Indexing block $currentBlockNumber")
                val block = thorService.getBlock(currentBlockNumber)
                blockRepo.save(block)
                currentBlockNumber++
            }
        } catch (e: Exception) {
            logger.error("Error while indexing block $currentBlockNumber", e)
            logger.info("Restarting block indexer in 10s...")
            Thread.sleep(10000)
            run()
        }
    }

    fun initialise() {
        val maxBlockNumber = blockRepo.getMaxBlockNumber().firstOrNull()?.number ?: 0
        currentBlockNumber = maxBlockNumber
        logger.info("Starting block indexer from block $maxBlockNumber...")
    }

    companion object {
        private val logger = LogManager.getLogger(BlockIndexer::class.java)
    }
}