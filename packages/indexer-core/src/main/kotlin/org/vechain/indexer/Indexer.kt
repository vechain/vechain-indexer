package org.vechain.indexer

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.vechain.indexer.exception.BlockNotFoundException
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
    private val numBlocksToPurge: Long = 12L
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

    fun start() {
        val block = getLastSyncedBlock()

        currentBlockNumber = block.number + 1
        previousBlockId = block.id

        // As a precaution assume a reorg happened
        resolveReorg()

        logger.info("Starting @ Block: $currentBlockNumber")
        run()
    }

    private tailrec fun run() {
        try {
            backoffDelay()

            val block = getBlockFromChain(currentBlockNumber)

            // Check for reorg.
            if (previousBlockId != genesisBlockId && previousBlockId != block.parentID)
                throw ReorgException("Reorg detected")

            logger.info("Processing @ Block $currentBlockNumber (${status})")
            processBlock(block)

            postProcessBlock(block)
        } catch (ex: BlockNotFoundException) {
            logger.info("Block Not Found @ $currentBlockNumber")
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
                "Success @ Block $currentBlockNumber (${timeSinceLastBlock}ms since mine)"
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
            val bestBlock = getBlockFromChain()
            if (bestBlock.number > currentBlockNumber) {
                logger.info("$name - Changing status to SYNCING (indexerBlock=${currentBlockNumber}, bestBlock=${bestBlock.number})")
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
     * resolveReorg will delete all the records in a given DB, between the current block and `numBlocksToPurge`
     *
     * It will set the previousBlockId to the most recent record in the DB. This allows us to call this function recursively until the reorg is resolved.
     */
    private fun resolveReorg() {
        // Delete all records from the previous n blocks
        purgeRecords(
            maxOf(currentBlockNumber - numBlocksToPurge - 1, 0),
            maxOf(currentBlockNumber + 1, 1)
        )

        val block = getLastSyncedBlock()

        currentBlockNumber = block.number + 1
        previousBlockId = block.id
        status = Status.SYNCING
    }

    /**
     * getBlockFromChain will return the block from the chain, or throw a BlockNotFoundException if it doesn't exist.
     * If no blockNumber is provided the best block will be returned.
     */
    abstract fun getBlockFromChain(blockNumber: Long? = null): Block

    /**
     * getLastSyncedBlock will return the last block that was successfully processed.
     */
    abstract fun getLastSyncedBlock(): Block

    /**
     * purgeRecords will delete all records between the startBlock and endBlock (inclusive)
     */
    abstract fun purgeRecords(startBlock: Long, endBlock: Long)

    /**
     * processBlock contains the business logic for this indexer.
     */
    abstract fun processBlock(block: Block)

}
