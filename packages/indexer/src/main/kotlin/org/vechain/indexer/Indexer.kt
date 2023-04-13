package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.vechain.indexer.exception.IndexerFullySynchronizedException

enum class Status {
    SYNCING, FULLY_SYNCED
}

const val APPROX_BLOCK_PERIOD = 9990L

abstract class Indexer {

    val name: String
        get() = this.javaClass.simpleName

    private val logger = LoggerFactory.getLogger(this::class.java)

    var status = Status.SYNCING
    var currentBlock: Long = 0

    fun start() {
        currentBlock = getStartingBlock()
        logger.info("Starting @ Block: $currentBlock")
        run()
    }

    private tailrec fun run() {
        try {
            backoffDelay()

            logger.info("Processing @ Block $currentBlock (${status})")

            processBlock(currentBlock)

            currentBlock++
        } catch (e: IndexerFullySynchronizedException) {
            logger.info("FULLY_SYNCED @ Block $currentBlock")
            Thread.sleep(1000)
            status = Status.FULLY_SYNCED
        } catch (e: Exception) {
            logger.error("Error while processing block $currentBlock", e)
            logger.info("Restarting indexer in 10s...")
            Thread.sleep(10 * 1000)
            status = Status.SYNCING
        }

        run()
    }

    private fun backoffDelay() {
        if (status == Status.FULLY_SYNCED) {
            Thread.sleep(APPROX_BLOCK_PERIOD)
        }
    }

    abstract fun processBlock(blockNumber: Long)
    abstract fun getStartingBlock(): Long


}