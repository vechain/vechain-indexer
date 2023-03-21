package org.vechain.indexer

import org.apache.logging.log4j.LogManager

abstract class Indexer {

    private var currentBlockNumber: Long = 0
    fun run() {
        try {
            logger.info("Starting block indexer...")
            currentBlockNumber = getStartingBlock()
            while (true) {
                processBlock(currentBlockNumber)
                currentBlockNumber++
            }
        } catch (e: Exception) {
            logger.error("Error while processing block $currentBlockNumber", e)
            logger.info("Restarting indexer in 10s...")
            Thread.sleep(10000)
            run()
        }
    }

    abstract fun processBlock(blockNumber: Long)
    abstract fun getStartingBlock(): Long

    companion object {
        private val logger = LogManager.getLogger(Indexer::class.java)
    }
}