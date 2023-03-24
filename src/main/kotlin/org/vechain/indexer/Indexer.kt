package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.vechain.indexer.exception.IndexerFullySynchronizedException

enum class Status {
    SYNCING, FULLY_SYNCED
}

const val APPROX_BLOCK_PERIOD = 9990L

abstract class Indexer {

    private val logger = LogManager.getLogger(this::class.simpleName)

    private var status = Status.SYNCING
    private var currentBlock: Long = 0

    fun start() {
        currentBlock = getStartingBlock()
        logger.info("Starting ${name()} from block $currentBlock")
        run()
    }

    private tailrec fun run() {
        try {
            backoffDelay()

            logger.info("${name()} is processing block $currentBlock")
            processBlock(currentBlock)

            currentBlock++
        } catch (e: IndexerFullySynchronizedException) {
            logger.info("${name()} is fully synchronized...")
            Thread.sleep(generateRandomDelay(1000, 3000))
            status = Status.FULLY_SYNCED
        } catch (e: Exception) {
            logger.error("${name()}: Error while processing block $currentBlock", e)
            logger.info("${name()}: Restarting indexer in 10s...")
            Thread.sleep(10000)
        }

        run()
    }

    private fun backoffDelay() {
        if (status == Status.FULLY_SYNCED) {
            Thread.sleep(APPROX_BLOCK_PERIOD)
        }
    }

    private fun generateRandomDelay(lower: Long, upper: Long): Long {
        return (Math.random() * (upper - lower) + lower).toLong()
    }

    abstract fun processBlock(blockNumber: Long)
    abstract fun getStartingBlock(): Long

    private fun name(): String {
        return this.javaClass.simpleName
    }

}