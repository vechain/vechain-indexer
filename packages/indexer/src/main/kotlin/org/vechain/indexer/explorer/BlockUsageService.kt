package org.vechain.indexer.explorer

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.explorer.TimestampUtils.calculateTimeBoundary
import org.vechain.indexer.explorer.TimestampUtils.isDaily
import org.vechain.indexer.explorer.TimestampUtils.isHourly
import org.vechain.indexer.explorer.TimestampUtils.isMonthly
import org.vechain.indexer.explorer.TimestampUtils.isWeekly
import org.vechain.indexer.explorer.repository.BlockUsageRepository
import org.vechain.indexer.thor.HexUtils.toBigInteger
import org.vechain.indexer.thor.model.Block

@Profile("explorer", "block-usage")
@Service
open class BlockUsageService(private val repository: BlockUsageRepository) {
    // Cache to store the last processed block usage to avoid DB lookups for sequential processing
    @Volatile private var lastProcessedBlockUsage: BlockUsage? = null

    /**
     * Process a block and create a BlockUsage record with cumulative statistics.
     *
     * @param block The block to process
     * @return BlockUsage record with cumulative data
     * @throws IllegalArgumentException if previous block record is missing for non-genesis blocks
     */
    open fun processBlock(block: Block): BlockUsage {
        val previousBlockUsage = getPreviousBlockUsage(block.number)
        validatePreviousBlockUsage(previousBlockUsage, block.number)

        val blockUsage =
            if (previousBlockUsage == null) {
                createGenesisBlockUsage(block)
            } else {
                createBlockUsage(block, previousBlockUsage)
            }

        // Cache the result for the next sequential block
        lastProcessedBlockUsage = blockUsage

        return blockUsage
    }

    /**
     * Get the previous block usage record from cache or repository. For sequential block
     * processing, this will use the cached value from the last processed block, avoiding a database
     * lookup.
     *
     * @param blockNumber The current block number
     * @return Previous BlockUsage record or null if genesis block
     */
    internal fun getPreviousBlockUsage(blockNumber: Long): BlockUsage? {
        if (blockNumber == 0L) {
            return null
        }

        // Check if we have the previous block cached
        val cached = lastProcessedBlockUsage
        if (cached != null && cached.blockNumber == blockNumber - 1) {
            return cached
        }

        // Cache miss - fall back to database lookup
        return repository.findByIdOrNull((blockNumber - 1).toString())
    }

    /**
     * Validate that previous block usage exists for non-genesis blocks.
     *
     * @param previousBlockUsage The previous block usage record
     * @param currentBlockNumber The current block number
     * @throws IllegalArgumentException if validation fails
     */
    internal fun validatePreviousBlockUsage(
        previousBlockUsage: BlockUsage?,
        currentBlockNumber: Long,
    ) {
        require(previousBlockUsage != null || currentBlockNumber == 0L) {
            "Previous block usage record should exist for block $currentBlockNumber"
        }
    }

    /**
     * Create a BlockUsage record for the genesis block (block 0).
     *
     * @param block The genesis block
     * @return BlockUsage record with initial values
     */
    internal fun createGenesisBlockUsage(block: Block): BlockUsage =
        BlockUsage(
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            cumulativeGasLimit = BigInteger.valueOf(block.gasLimit),
            cumulativeGasUsed = BigInteger.valueOf(block.gasUsed),
            cumulativeBaseFeePerGas = parseBaseFeePerGas(block.baseFeePerGas),
            cumulativeNumTransactions = block.transactions.size.toBigInteger(),
            cumulativeNumClauses = calculateTotalClauses(block),
            isHourly =
                calculateTimeBoundary(
                    maxOf(0L, block.timestamp - 10L),
                    block.timestamp,
                    ::isHourly,
                ),
            isDaily =
                calculateTimeBoundary(maxOf(0L, block.timestamp - 10L), block.timestamp, ::isDaily),
            isWeekly =
                calculateTimeBoundary(
                    maxOf(0L, block.timestamp - 10L),
                    block.timestamp,
                    ::isWeekly,
                ),
            isMonthly =
                calculateTimeBoundary(
                    maxOf(0L, block.timestamp - 10L),
                    block.timestamp,
                    ::isMonthly,
                ),
        )

    /**
     * Create a BlockUsage record with cumulative values from previous block.
     *
     * @param block The current block
     * @param previousBlockUsage The previous block usage record
     * @return BlockUsage record with cumulative statistics
     */
    internal fun createBlockUsage(block: Block, previousBlockUsage: BlockUsage): BlockUsage =
        BlockUsage(
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            cumulativeGasLimit = calculateCumulativeGasLimit(previousBlockUsage, block),
            cumulativeGasUsed = calculateCumulativeGasUsed(previousBlockUsage, block),
            cumulativeBaseFeePerGas = calculateCumulativeBaseFeePerGas(previousBlockUsage, block),
            cumulativeNumTransactions = calculateCumulativeTransactions(previousBlockUsage, block),
            cumulativeNumClauses = calculateCumulativeClauses(previousBlockUsage, block),
            isHourly =
                calculateTimeBoundary(
                    previousBlockUsage.blockTimestamp,
                    block.timestamp,
                    ::isHourly,
                ),
            isDaily =
                calculateTimeBoundary(
                    previousBlockUsage.blockTimestamp,
                    block.timestamp,
                    ::isDaily,
                ),
            isWeekly =
                calculateTimeBoundary(
                    previousBlockUsage.blockTimestamp,
                    block.timestamp,
                    ::isWeekly,
                ),
            isMonthly =
                calculateTimeBoundary(
                    previousBlockUsage.blockTimestamp,
                    block.timestamp,
                    ::isMonthly,
                ),
        )

    /**
     * Parse base fee per gas from hex string to BigInteger.
     *
     * @param baseFeePerGas Hex string with optional "0x" prefix
     * @return BigInteger value or null if input is null
     */
    internal fun parseBaseFeePerGas(baseFeePerGas: String?): BigInteger? =
        baseFeePerGas?.let { toBigInteger(it) }

    /**
     * Calculate total number of clauses in a block.
     *
     * @param block The block to analyze
     * @return Total number of clauses as BigInteger
     */
    internal fun calculateTotalClauses(block: Block): BigInteger =
        block.transactions.sumOf { it.clauses.size }.toBigInteger()

    /**
     * Calculate cumulative gas limit.
     *
     * @param previousBlockUsage Previous block usage record
     * @param block Current block
     * @return Cumulative gas limit
     */
    internal fun calculateCumulativeGasLimit(
        previousBlockUsage: BlockUsage,
        block: Block,
    ): BigInteger = previousBlockUsage.cumulativeGasLimit + BigInteger.valueOf(block.gasLimit)

    /**
     * Calculate cumulative gas used.
     *
     * @param previousBlockUsage Previous block usage record
     * @param block Current block
     * @return Cumulative gas used
     */
    internal fun calculateCumulativeGasUsed(
        previousBlockUsage: BlockUsage,
        block: Block,
    ): BigInteger = previousBlockUsage.cumulativeGasUsed + BigInteger.valueOf(block.gasUsed)

    /**
     * Calculate cumulative base fee per gas.
     *
     * @param previousBlockUsage Previous block usage record
     * @param block Current block
     * @return Cumulative base fee per gas or null
     */
    internal fun calculateCumulativeBaseFeePerGas(
        previousBlockUsage: BlockUsage,
        block: Block,
    ): BigInteger? {
        val currentBaseFee = parseBaseFeePerGas(block.baseFeePerGas) ?: return null
        val previousCumulativeBaseFee =
            previousBlockUsage.cumulativeBaseFeePerGas ?: BigInteger.ZERO
        return previousCumulativeBaseFee + currentBaseFee
    }

    /**
     * Calculate cumulative number of transactions.
     *
     * @param previousBlockUsage Previous block usage record
     * @param block Current block
     * @return Cumulative number of transactions
     */
    internal fun calculateCumulativeTransactions(
        previousBlockUsage: BlockUsage,
        block: Block,
    ): BigInteger =
        previousBlockUsage.cumulativeNumTransactions + block.transactions.size.toBigInteger()

    /**
     * Calculate cumulative number of clauses.
     *
     * @param previousBlockUsage Previous block usage record
     * @param block Current block
     * @return Cumulative number of clauses
     */
    internal fun calculateCumulativeClauses(
        previousBlockUsage: BlockUsage,
        block: Block,
    ): BigInteger = previousBlockUsage.cumulativeNumClauses + calculateTotalClauses(block)

    /**
     * Save a BlockUsage record to the repository.
     *
     * @param blockUsage The BlockUsage record to save
     */
    @Transactional(rollbackFor = [Exception::class])
    open fun save(blockUsage: BlockUsage) {
        repository.save(blockUsage)
    }
}
