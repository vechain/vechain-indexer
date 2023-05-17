package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.ReorgException
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.BaseIndexedRepo
import org.vechain.indexer.service.ThorService
import java.time.LocalDateTime
import java.time.ZoneOffset

enum class Status {
    SYNCING, FULLY_SYNCED
}

const val INITIAL_BACKOFF_PERIOD = 10000L

abstract class Indexer(
    private val thorService: ThorService,
    private val repo: BaseIndexedRepo<*>,
    private val numBlocksToPurge: Long = 12L
) {

    @Value("\${genesis.block.id:0x00000000c05a20fbca2bf6ae3affba6af4a74b800b585bf7a4988aba7aea69f6}")
    protected val genesisBlockId: String = "0x00000000c05a20fbca2bf6ae3affba6af4a74b800b585bf7a4988aba7aea69f6"

    val name: String
        get() = this.javaClass.simpleName

    private val logger = LoggerFactory.getLogger(this::class.java)

    var status = Status.SYNCING
    var currentBlockNumber: Long = 0
        private set
    var timeLastProcessed: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
        private set
    private var previousBlockId: String = genesisBlockId
    private var backoffPeriod = INITIAL_BACKOFF_PERIOD

    fun start() {
        currentBlockNumber = getPreviousBlockNumber() + 1

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
        // If we are fully synced, recalculate the backoff period.
        if (status == Status.FULLY_SYNCED) {
            val currentEpoch = LocalDateTime.now(ZoneOffset.UTC).toInstant(ZoneOffset.UTC).toEpochMilli()
            val timeSinceLastBlock = maxOf(currentEpoch - block.blockTimestamp.times(1000), 0)
            backoffPeriod = maxOf(0, INITIAL_BACKOFF_PERIOD - (timeSinceLastBlock)) + 1000

            logger.info(
                "Success @ Block $currentBlockNumber (${timeSinceLastBlock}ms since mine)"
            )
        }

        // Increment the current block.
        currentBlockNumber++

        // Set the previous block id.
        previousBlockId = block.blockId

        timeLastProcessed = LocalDateTime.now(ZoneOffset.UTC)
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
        repo.deleteAllByBlockNumberBetween(
            maxOf(currentBlockNumber - numBlocksToPurge - 1, 0),
            maxOf(currentBlockNumber + 1, 1)
        )

        currentBlockNumber = getPreviousBlockNumber() + 1
        previousBlockId = getPreviousBlockId()

        status = Status.SYNCING
    }

    fun getPreviousBlockNumber(): Long {
        return repo.getMaxBlockNumber() ?: -1
    }

    private fun getPreviousBlockId(): String {
        return repo.getMaxBlockId() ?: genesisBlockId
    }

    abstract fun processBlock(block: Block)

}
