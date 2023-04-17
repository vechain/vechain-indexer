package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.ReorgException
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.IndexerRepository
import org.vechain.indexer.service.ThorService
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class Status {
    SYNCING, FULLY_SYNCED
}

const val ZERO_ID = "0x000000000"
const val INITIAL_BACKOFF_PERIOD = 10000L

abstract class Indexer(
    private val thorService: ThorService,
    private val repo: IndexerRepository,
    private val numBlocksToPurge: Long = 12L
) {

    val name: String
        get() = this.javaClass.simpleName

    private val logger = LoggerFactory.getLogger(this::class.java)

    var status = Status.SYNCING
    var currentBlockNumber: Long = 0
    private var previousBlockId: String = ZERO_ID
    private var backoffPeriod = INITIAL_BACKOFF_PERIOD

    fun start() {
        currentBlockNumber = getStartingBlock()

        // As a precaution assume a reorg happened
        resolveReorg()

        logger.info("Starting @ Block: $currentBlockNumber")
        run()
    }

    private tailrec fun run() {
        try {
            backoffDelay()

            val block = thorService.getBlock(currentBlockNumber)

            // Check for reorg.
            if (previousBlockId != ZERO_ID && previousBlockId != block.parentID)
                throw ReorgException("Reorg detected")

            logger.info("Processing @ Block $currentBlockNumber (${status})")
            processBlock(block)

            postProcessBlock(block)
        } catch (ex: BlockNotFoundException) {
            logger.info("Not Found @ Block $currentBlockNumber")
            backoffPeriod = 4000

            if (ex.blockNumber == currentBlockNumber)
                status = Status.FULLY_SYNCED

        } catch (e: ReorgException) {
            logger.error("REORG @ Block $currentBlockNumber")
            resolveReorg()
            logger.info("Restarting indexer @ Block $currentBlockNumber after resolving reorg...")
        } catch (e: Exception) {
            logger.error("Error while processing block $currentBlockNumber", e)
            logger.info("Restarting indexer in 10s...")
            Thread.sleep(INITIAL_BACKOFF_PERIOD)
            status = Status.SYNCING
        }

        run()
    }

    private fun postProcessBlock(block: Block) {
        // If we are fully synced, recalculate the backoff period.
        if (status == Status.FULLY_SYNCED) {
            val currentEpoch = LocalDateTime.now(ZoneOffset.UTC).toInstant(ZoneOffset.UTC).toEpochMilli()
            val timeSinceLastBlock = maxOf(currentEpoch - block.timestamp.times(1000), 0)
            backoffPeriod = maxOf(0, INITIAL_BACKOFF_PERIOD - (timeSinceLastBlock))
        }

        // Increment the current block.
        currentBlockNumber++

        // Set the previous block id.
        previousBlockId = block.id
    }

    private fun backoffDelay() {
        if (status == Status.FULLY_SYNCED) {
            Thread.sleep(backoffPeriod)
        }
    }

    private fun resolveReorg() {
        // Delete all records from the previous n blocks
        repo.deleteAllByBlockNumberBetween(
            maxOf(currentBlockNumber - numBlocksToPurge - 1, 1),
            maxOf(currentBlockNumber + 1, 1)
        )

        currentBlockNumber = maxOf(currentBlockNumber - numBlocksToPurge, 0)
        previousBlockId = ZERO_ID
        status = Status.SYNCING
    }

    fun getStartingBlock(): Long {
        return repo.getMaxBlockNumber()?.blockNumber ?: 0
    }

    abstract fun processBlock(block: Block)

}
