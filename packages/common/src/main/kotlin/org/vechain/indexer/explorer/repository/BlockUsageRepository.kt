package org.vechain.indexer.explorer.repository

import org.vechain.indexer.explorer.BlockUsage
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface BlockUsageRepository : PostgresIndexedRepository {

    /**
     * Save a BlockUsage record.
     *
     * @param blockUsage The record to save
     */
    fun save(blockUsage: BlockUsage)

    /**
     * Save multiple BlockUsage records.
     *
     * @param blockUsages The records to save
     */
    fun saveAll(blockUsages: List<BlockUsage>)

    /**
     * Find a block usage record by block number.
     *
     * @param blockNumber The block number to find
     * @return BlockUsage record or null if not found
     */
    fun findByBlockNumber(blockNumber: Long): BlockUsage?

    /**
     * Find all block usage records within a timestamp range (inclusive).
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of BlockUsage records in the range, ordered by timestamp ascending
     */
    fun findAllInTimestampRange(startTimestamp: Long, endTimestamp: Long): List<BlockUsage>

    /**
     * Find hourly block usage records within a timestamp range (inclusive). Returns blocks where
     * isHourly is true, plus the first and last records in the range as boundaries.
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of hourly BlockUsage records in the range plus boundaries, ordered by timestamp
     *   ascending
     */
    fun findHourlyInTimestampRange(startTimestamp: Long, endTimestamp: Long): List<BlockUsage>

    /**
     * Find daily block usage records within a timestamp range (inclusive). Returns blocks where
     * isDaily is true.
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of daily BlockUsage records in the range, ordered by timestamp ascending
     */
    fun findDailyInTimestampRange(startTimestamp: Long, endTimestamp: Long): List<BlockUsage>

    /**
     * Find weekly block usage records within a timestamp range (inclusive). Returns blocks where
     * isWeekly is true.
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of weekly BlockUsage records in the range, ordered by timestamp ascending
     */
    fun findWeeklyInTimestampRange(startTimestamp: Long, endTimestamp: Long): List<BlockUsage>

    /**
     * Find monthly block usage records within a timestamp range (inclusive). Returns blocks where
     * isMonthly is true.
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of monthly BlockUsage records in the range, ordered by timestamp ascending
     */
    fun findMonthlyInTimestampRange(startTimestamp: Long, endTimestamp: Long): List<BlockUsage>
}
