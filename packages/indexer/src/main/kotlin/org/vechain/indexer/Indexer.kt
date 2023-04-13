package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.vechain.indexer.exception.IndexerFullySynchronizedException
import org.vechain.indexer.model.Block
import org.vechain.indexer.service.ThorService
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.max

enum class Status {
    SYNCING, FULLY_SYNCED
}

const val INITIAL_BACKOFF_PERIOD = 10000L

abstract class Indexer(private val thorService: ThorService) {

    val name: String
        get() = this.javaClass.simpleName

    private val logger = LoggerFactory.getLogger(this::class.java)

    var status = Status.SYNCING
    var currentBlock: Long = 0
    private var backoffPeriod = INITIAL_BACKOFF_PERIOD

    fun start() {
        currentBlock = getStartingBlock()
        logger.info("Starting @ Block: $currentBlock")
        run()
    }

    private tailrec fun run() {
        try {
            backoffDelay()

            logger.info("Processing @ Block $currentBlock (${status})")
            val block = thorService.getBlock(currentBlock)
            processBlock(block)
            
            postProcessBlock(block)
        } catch (e: IndexerFullySynchronizedException) {
            logger.info("FULLY_SYNCED @ Block $currentBlock")
            backoffPeriod = 4000
            status = Status.FULLY_SYNCED
        } catch (e: Exception) {
            logger.error("Error while processing block $currentBlock", e)
            logger.info("Restarting indexer in 10s...")
            Thread.sleep(10 * 1000)
            status = Status.SYNCING
        }

        run()
    }

    private fun postProcessBlock(block: Block) {
        // If we are fully synced, recalculate the backoff period.
        if (status == Status.FULLY_SYNCED) {
            val currentEpoch = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC)
            val timeSinceLastBlock =
                max(currentEpoch - block.timestamp!!, 0)
            logger.info("${name()} currentEpoch $currentEpoch blocktimestamp ${block.timestamp} time since last block: $timeSinceLastBlock")
            backoffPeriod = maxOf(0, INITIAL_BACKOFF_PERIOD - (timeSinceLastBlock * 1000))
        }

        // Increment the current block.
        currentBlock++
    }

    private fun backoffDelay() {
        if (status == Status.FULLY_SYNCED) {
            logger.info("${name()} is backing off for $backoffPeriod ms...")
            Thread.sleep(backoffPeriod)
        }
    }

    abstract fun processBlock(block: Block)
    abstract fun getStartingBlock(): Long

}