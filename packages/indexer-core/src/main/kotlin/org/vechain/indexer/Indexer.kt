package org.vechain.indexer

import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.ReorgException
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block

/** The possible states the indexer can be */
enum class Status {
    /** Indexer is processing blocks */
    SYNCING,
    /** Indexing is up-to-date with the latest on-chain block */
    FULLY_SYNCED,
    /** A chain re-organization has been detected during processing */
    REORG,
    /** Indexer encountered an unknown exception during processing */
    ERROR
}

/** Initial processing backoff duration */
const val INITIAL_BACKOFF_PERIOD = 10_000L

abstract class Indexer(
    protected open val thorClient: ThorClient,
    private val startBlock: Long = 0L,
    private val syncLoggerInterval: Long = 1000L,
) {

    /** ID of the block before the currently latest block processed */
    private var previousBlockId: String? = null

    /**
     * Number of indexer iterations remaining in case a given number of iterations has been
     * specified
     */
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

    /** Initialises the indexer processing */
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

    /** Starts the indexer processing */
    suspend fun start(iterations: Long? = null) {
        remainingIterations = iterations

        // Initialise the indexer
        initialise()

        logger.info("Starting @ Block: $currentBlockNumber")
        run()
    }

    /** Restarts the processing based on the current indexer status */
    private suspend fun restart() {
        // Initialise the indexer
        when (status) {
            Status.ERROR -> initialise(currentBlockNumber)
            Status.REORG -> initialise(currentBlockNumber - 1)
            else -> initialise()
        }

        logger.info("Restarting indexer @ Block: $currentBlockNumber")
    }

    /** The core indexer logic */
    private tailrec suspend fun run() {
        try {
            if (hasNoRemainingIterations()) return

            backoffDelay()

            if (status == Status.ERROR || status == Status.REORG) restart()

            val block = getBlockFromChain(currentBlockNumber)

            // Check for chain re-organization.
            if (currentBlockNumber > startBlock && previousBlockId != block.parentID)
                throw ReorgException(
                    "Chain re-organization detected @ Block $currentBlockNumber with parent block ID ${block.parentID}"
                )

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
        } catch (e: ReorgException) {
            logger.error("REORG @ Block $currentBlockNumber")
            handleReorg()
        } catch (e: Exception) {
            logger.error("Error while processing block $currentBlockNumber", e)
            handleError()
        }

        run()
    }

    /**
     * Checks whether there are remaining indexer iterations in case a given number of iterations
     * has been specified
     *
     * @return whether indexer has remaining iterations
     */
    private fun hasNoRemainingIterations(): Boolean {
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

    /**
     * Ensures that indexer is fully synced, recalculates the backoff period & increments the
     * current block number
     *
     * @param block the block to undergo post-processing
     */
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

    /** Ensures that indexer is not behind on-chain best block when in fully synced state */
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
     * Returns the block identified by its number from the chain, or throw a BlockNotFoundException
     * if it doesn't exist.
     *
     * @param blockNumber the block number
     * @return the block corresponding to the number
     * @throws BlockNotFoundException if no block is found with that number
     */
    private suspend fun getBlockFromChain(blockNumber: Long): Block {
        return thorClient.getBlock(blockNumber)
    }

    /**
     * Returns the latest block from the chain
     *
     * @return the chain best block
     * @throws BlockNotFoundException if not found
     */
    private suspend fun getBestBlockFromChain(): Block {
        return thorClient.getBestBlock()
    }

    /**
     * Returns the last block that was successfully processed.
     *
     * @return last synced block number
     */
    abstract fun getLastSyncedBlockNumber(): Long

    /**
     * Rolls back changes made in the given block number. The block number will always be the last
     * synchronized block. It is provided as a parameter here for convenience.
     *
     * @param blockNumber the block number to be rolled back
     */
    abstract fun rollback(blockNumber: Long)

    /**
     * Holds the business logic for this indexer.
     *
     * @param block the block to be processed
     */
    abstract fun processBlock(block: Block)
}
