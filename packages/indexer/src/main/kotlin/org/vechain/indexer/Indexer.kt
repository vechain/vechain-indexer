package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.vechain.indexer.exception.IndexerFullySynchronizedException

enum class Status {
    SYNCING, FULLY_SYNCED
}

const val INITIAL_BACKOFF_PERIOD = 9650L
const val MAX_BACKOFF_PERIOD = 9950L

abstract class Indexer {

    protected val logger = LogManager.getLogger(this::class.simpleName)

    var status = Status.SYNCING
    var currentBlock: Long = 0
    private var backoffPeriod = INITIAL_BACKOFF_PERIOD

    fun start() {
        currentBlock = getStartingBlock()
        logger.info("Starting ${name()} from block $currentBlock")
        run()
    }

    private tailrec fun run() {
        try {
            backoffDelay()

            logger.info("${name()} is processing block $currentBlock (Status: $status)")
            processBlock(currentBlock)
            
            backoffPeriod = minOf(maxOf(INITIAL_BACKOFF_PERIOD, backoffPeriod + 25), MAX_BACKOFF_PERIOD)
            currentBlock++
        } catch (e: IndexerFullySynchronizedException) {
            logger.info("${name()} is fully synchronized...")
            backoffPeriod = 500
            status = Status.FULLY_SYNCED
        } catch (e: Exception) {
            logger.error("${name()}: Error while processing block $currentBlock", e)
            logger.info("${name()}: Restarting indexer in 10s...")
            Thread.sleep(10000)
            status = Status.SYNCING
        }

        run()
    }

    private fun backoffDelay() {
        if (status == Status.FULLY_SYNCED) {
            logger.debug("Backing off for $backoffPeriod ms...")
            Thread.sleep(backoffPeriod)
        }
    }

    abstract fun processBlock(blockNumber: Long)
    abstract fun getStartingBlock(): Long

    internal fun name(): String {
        return this.javaClass.simpleName
    }

}