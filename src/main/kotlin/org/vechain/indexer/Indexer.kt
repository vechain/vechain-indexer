package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.vechain.indexer.exception.IndexerFullySynchronizedException


const val APPROX_BLOCK_PERIOD = 9990L
abstract class Indexer {
    private var backoffDelay = 0L
    private val shortBackoffPeriod = generateRandomDelay(1000, 3000)

    fun run() {
        logger.info("Starting block indexer...")
        run(getStartingBlock())
    }
    private tailrec fun run(blockNumber: Long) {
        var nextBlock = blockNumber
        try {
            if (backoffDelay > 0) Thread.sleep(backoffDelay)

            processBlock(blockNumber)

            if (backoffDelay == shortBackoffPeriod) backoffDelay = APPROX_BLOCK_PERIOD

            nextBlock++
        } catch (e: IndexerFullySynchronizedException) {
            logger.info("Indexer fully synchronized...")
            backoffDelay = shortBackoffPeriod
        }
        catch (e: Exception) {
            logger.error("Error while processing block $blockNumber", e)
            logger.info("Restarting indexer in 10s...")
            Thread.sleep(10000)
        }
        
        run(nextBlock)
    }

    private fun generateRandomDelay(lower: Long, upper: Long): Long {
        return (Math.random() * (upper - lower) + lower).toLong()
    }

    abstract fun processBlock(blockNumber: Long)
    abstract fun getStartingBlock(): Long

    companion object {
        private val logger = LogManager.getLogger(Indexer::class.java)
    }
}