package org.vechain.indexer

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.FullySynchronisedException
import org.vechain.indexer.exception.ReorgException
import org.vechain.thor.model.Block

enum class Status {
    SYNCING,
    FULLY_SYNCED,
    REORG,
    ERROR
}

const val INITIAL_BACKOFF_PERIOD = 10_000L

abstract class Indexer(
    protected open val thorClient: ThorClient,
    private val startBlock: Long = 0L,
    private val syncLoggerInterval: Long = 1000L,
) {

    private var previousBlockId: String? = null

    private var remainingIterations: Long? = null

    val name: String
        get() = this.javaClass.simpleName

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    var status = Status.SYNCING
        private set

    var currentBlockNumber: Long = 0
        private set

    var timeLastProcessed: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
        private set

    private var backoffPeriod = 0L

    private suspend fun initialise(
        blockNumber: Long = maxOf(getLastSyncedBlockNumber(), startBlock)
    ) {

        // To ensure data integrity roll back changes made in the last block
        rollback(blockNumber)

        // Initialise fields
        currentBlockNumber = blockNumber
        status = Status.SYNCING
        previousBlockId = getBlockFromChain(maxOf(blockNumber - 1, 0)).id
    }

    suspend fun start(iterations: Long? = null) {
        remainingIterations = iterations

        // Initialise the indexer
        initialise()

        logger.info("Starting @ Block: $currentBlockNumber")
        run()
    }

    private suspend fun restart() {
        // Initialise the indexer
        if (status == Status.ERROR) initialise(currentBlockNumber)
        else if (status == Status.REORG) initialise(currentBlockNumber - 1) else initialise()

        logger.info("Restarting indexer @ Block: $currentBlockNumber")
    }

    private tailrec suspend fun run() {
        try {
            if (hasIndexerFinished()) return

            backoffDelay()

            if (status == Status.ERROR || status == Status.REORG) restart()

            val block = getBlockFromChain(currentBlockNumber)

            // Check for reorg.
            if (currentBlockNumber > startBlock && previousBlockId != block.parentID)
                throw ReorgException("Reorg detected")

            if (logger.isDebugEnabled)
                logger.debug("Processing @ Block $currentBlockNumber ($status)")
            else if (status != Status.SYNCING || currentBlockNumber % syncLoggerInterval == 0L)
                logger.info("Processing @ Block $currentBlockNumber ($status)")

            processBlock(block)

            postProcessBlock(block)
        } catch (ex: BlockNotFoundException) {
            logger.info("Block $currentBlockNumber not found. Indexer may be fully synchronised.")
            handleFullySynced()
            ensureFullySynced()
        } catch (ex: FullySynchronisedException) {
            logger.info("Fully synchronised @ Block $currentBlockNumber")
            handleFullySynced()
        } catch (e: ReorgException) {
            logger.error("REORG @ Block $currentBlockNumber")
            handleReorg()
        } catch (e: Exception) {
            logger.error("Error while processing block $currentBlockNumber", e)
            handleError()
        }

        run()
    }

    private fun hasIndexerFinished(): Boolean {
        if (remainingIterations != null) {
            if (remainingIterations!! <= 0) {
                logger.info("Indexer finished at block $currentBlockNumber")
                return true
            }
            remainingIterations = remainingIterations?.dec()
        }
        return false
    }

    private fun handleFullySynced() {
        backoffPeriod = 4000
        status = Status.FULLY_SYNCED
    }

    private fun handleError() {
        backoffPeriod = INITIAL_BACKOFF_PERIOD
        status = Status.ERROR
    }

    private fun handleReorg() {
        backoffPeriod = INITIAL_BACKOFF_PERIOD
        status = Status.REORG
    }

    private suspend fun postProcessBlock(block: Block) {

        // Every 20 blocks, check if we are fully synced.
        if (status == Status.FULLY_SYNCED && currentBlockNumber % 20 == 0L) {
            ensureFullySynced()
        }

        // If we are fully synced, recalculate the backoff period.
        if (status == Status.FULLY_SYNCED) {
            val currentEpoch =
                LocalDateTime.now(ZoneOffset.UTC).toInstant(ZoneOffset.UTC).toEpochMilli()
            val timeSinceLastBlock = maxOf(currentEpoch - block.timestamp.times(1000), 0)
            backoffPeriod = maxOf(0, INITIAL_BACKOFF_PERIOD - (timeSinceLastBlock)) + 100

            logger.info("Success @ Block $currentBlockNumber ($timeSinceLastBlock ms since mine)")
        }

        // Increment the current block.
        currentBlockNumber++

        // Set the previous block id.
        previousBlockId = block.id

        timeLastProcessed = LocalDateTime.now(ZoneOffset.UTC)
    }

    private suspend fun ensureFullySynced() {
        if (status == Status.FULLY_SYNCED) {
            val latestBlock = getBestBlockFromChain()
            if (latestBlock.number > currentBlockNumber) {
                logger.info(
                    "$name - Changing status to SYNCING (indexerBlock=${currentBlockNumber}, latestBlock=${latestBlock.number})"
                )
                status = Status.SYNCING
            }
        }
    }

    private suspend fun backoffDelay() {
        if (status != Status.SYNCING) {
            delay(backoffPeriod)
        }
    }

    /**
     * getBlockFromChain will return the block from the chain, or throw a BlockNotFoundException if
     * it doesn't exist.
     */
    private suspend fun getBlockFromChain(blockNumber: Long): Block {
        return thorClient.getBlock(blockNumber)
    }

    /**
     * getBestBlockFromChain will return the latest block from the chain, or throw a
     * BlockNotFoundException if it doesn't exist.
     */
    private suspend fun getBestBlockFromChain(): Block {
        return thorClient.getBestBlock()
    }

    /** getLastSyncedBlock will return the last block that was successfully processed. */
    abstract fun getLastSyncedBlockNumber(): Long

    /**
     * rollback will roll back changes made in the given block number. blockNumber will always be
     * the last synchronized block. It is provided as a parameter here for convenience.
     */
    abstract fun rollback(blockNumber: Long)

    /** processBlock contains the business logic for this indexer. */
    abstract fun processBlock(block: Block)
}
