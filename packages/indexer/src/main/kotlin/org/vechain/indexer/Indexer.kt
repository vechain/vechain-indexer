package org.vechain.indexer

import org.apache.logging.log4j.LogManager
import org.vechain.indexer.exception.IndexerFullySynchronizedException
import org.vechain.indexer.exception.IndexerSynchronizationException

enum class Status {
    SYNCING, FULLY_SYNCED
}

const val APPROX_BLOCK_PERIOD = 9990L

abstract class Indexer {

    protected val logger = LogManager.getLogger(this::class.simpleName)

    var status = Status.SYNCING
    var currentBlock: Long = 0

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

            currentBlock++
        } catch (e: IndexerFullySynchronizedException) {
            logger.info("${name()} is fully synchronized...")
            Thread.sleep(1000)
            status = Status.FULLY_SYNCED
        } catch (e: IndexerSynchronizationException) {
            logger.warn(e.message)
            Thread.sleep(5000)
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
            Thread.sleep(APPROX_BLOCK_PERIOD)
        }
    }

    abstract fun processBlock(blockNumber: Long)
    abstract fun getStartingBlock(): Long

    private fun name(): String {
        return this.javaClass.simpleName
    }

}