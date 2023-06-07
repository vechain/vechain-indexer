package org.vechain.indexer

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.FullySynchronisedException
import org.vechain.indexer.exception.ReorgException
import org.vechain.thor.model.Block
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class Status {
    SYNCING, FULLY_SYNCED
}

const val INITIAL_BACKOFF_PERIOD = 10_000L

abstract class Indexer(
    private var genesisBlockId: String,
) {

    private var previousBlockId: String = genesisBlockId

    val name: String
        get() = this.javaClass.simpleName

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    var status = Status.SYNCING
        private set

    var currentBlockNumber: Long = 0
        private set
    var timeLastProcessed: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
        private set

    private var backoffPeriod = INITIAL_BACKOFF_PERIOD

    private fun initialise() {
        val block = getLastSyncedBlock()

        // To ensure data integrity purge data from the last block
        purgeRecords(block.number)

        // Initialise fields
        currentBlockNumber = block.number
        status = Status.SYNCING
        previousBlockId = getBlockFromChain(maxOf(block.number - 1, 0)).id
    }

    fun start() {
        // Initialise the indexer
        initialise()

        logger.info("Starting @ Block: $currentBlockNumber")
        run()
    }

    private fun restart() {
        // Initialise the indexer
        initialise()

        // Wait for 10 seconds
        logger.info("Restarting indexer in 10s...")
        Thread.sleep(INITIAL_BACKOFF_PERIOD)

        logger.info("Restarting indexer @ Block: $currentBlockNumber")
    }

    private tailrec fun run() {
        try {
            backoffDelay()

            val block = getBlockFromChain(currentBlockNumber)

            // Check for reorg.
            if (previousBlockId != genesisBlockId && previousBlockId != block.parentID)
                throw ReorgException("Reorg detected")

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
            restart()
        } catch (e: Exception) {
            logger.error("Error while processing block $currentBlockNumber", e)
            restart()
        }

        run()
    }

    private fun handleFullySynced() {
        backoffPeriod = 4000
        status = Status.FULLY_SYNCED
    }

    private fun postProcessBlock(block: Block) {

        // Every 20 blocks, check if we are fully synced.
        if (status == Status.FULLY_SYNCED && currentBlockNumber % 20 == 0L) {
            ensureFullySynced()
        }

        // If we are fully synced, recalculate the backoff period.
        if (status == Status.FULLY_SYNCED) {
            val currentEpoch = LocalDateTime.now(ZoneOffset.UTC).toInstant(ZoneOffset.UTC).toEpochMilli()
            val timeSinceLastBlock = maxOf(currentEpoch - block.timestamp.times(1000), 0)
            backoffPeriod = maxOf(0, INITIAL_BACKOFF_PERIOD - (timeSinceLastBlock)) + 100

            logger.info(
                "Success @ Block $currentBlockNumber ($timeSinceLastBlock ms since mine)"
            )
        }

        // Increment the current block.
        currentBlockNumber++

        // Set the previous block id.
        previousBlockId = block.id

        timeLastProcessed = LocalDateTime.now(ZoneOffset.UTC)
    }

    private fun ensureFullySynced() {
        if (status == Status.FULLY_SYNCED) {
            val latestBlock = getLatestBlockFromChain()
            if (latestBlock.number > currentBlockNumber) {
                logger.info("$name - Changing status to SYNCING (indexerBlock=${currentBlockNumber}, latestBlock=${latestBlock.number})")
                status = Status.SYNCING
            }
        }
    }

    private fun backoffDelay() {
        if (status == Status.FULLY_SYNCED) {
            Thread.sleep(backoffPeriod)
        }
    }

    /**
     * getBlockFromChain will return the block from the chain, or throw a BlockNotFoundException if it doesn't exist.
     */
    abstract fun getBlockFromChain(blockNumber: Long): Block

    /**
     * getLatestBlockFromChain will return the latest block from the chain, or throw a BlockNotFoundException if it doesn't exist.
     * The latest block could be the best or finalized block depending on your use-case
     */
    abstract fun getLatestBlockFromChain(): Block

    /**
     * getLastSyncedBlock will return the last block that was successfully processed.
     */
    abstract fun getLastSyncedBlock(): Block

    /**
     * purgeRecords will delete all records for the given block number.
     */
    abstract fun purgeRecords(blockNumber: Long)

    /**
     * processBlock contains the business logic for this indexer.
     */
    abstract fun processBlock(block: Block)

}
