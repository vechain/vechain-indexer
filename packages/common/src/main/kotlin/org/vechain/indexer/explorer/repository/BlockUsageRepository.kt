package org.vechain.indexer.explorer.repository

import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.explorer.BlockUsage

@Repository
interface BlockUsageRepository : BaseIndexedRepository<BlockUsage, String> {

    /**
     * Find all block usage records within a timestamp range (inclusive).
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of BlockUsage records in the range, ordered by timestamp ascending
     */
    @Query(
        value = "{ 'blockTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
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
    @Query(
        value =
            "{ 'blockTimestamp': { \$gte: ?0, \$lte: ?1 }, \$or: [{ 'isHourly': true }, { 'blockTimestamp': ?0 }, { 'blockTimestamp': ?1 }] }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findHourlyInTimestampRange(startTimestamp: Long, endTimestamp: Long): List<BlockUsage>

    /**
     * Find daily block usage records within a timestamp range (inclusive). Returns blocks where
     * isDaily is true.
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of daily BlockUsage records in the range, ordered by timestamp ascending
     */
    @Query(
        value =
            "{ 'blockTimestamp': { \$gte: ?0, \$lte: ?1 }, \$or: [{ 'isDaily': true }, { 'blockTimestamp': ?0 }, { 'blockTimestamp': ?1 }] }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findDailyInTimestampRange(startTimestamp: Long, endTimestamp: Long): List<BlockUsage>

    /**
     * Find weekly block usage records within a timestamp range (inclusive). Returns blocks where
     * isWeekly is true.
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of weekly BlockUsage records in the range, ordered by timestamp ascending
     */
    @Query(
        value =
            "{ 'blockTimestamp': { \$gte: ?0, \$lte: ?1 }, \$or: [{ 'isWeekly': true }, { 'blockTimestamp': ?0 }, { 'blockTimestamp': ?1 }] }}",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findWeeklyInTimestampRange(startTimestamp: Long, endTimestamp: Long): List<BlockUsage>

    /**
     * Find monthly block usage records within a timestamp range (inclusive). Returns blocks where
     * isMonthly is true.
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of monthly BlockUsage records in the range, ordered by timestamp ascending
     */
    @Query(
        value =
            "{ 'blockTimestamp': { \$gte: ?0, \$lte: ?1 }, \$or: [{ 'isMonthly': true }, { 'blockTimestamp': ?0 }, { 'blockTimestamp': ?1 }] }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findMonthlyInTimestampRange(startTimestamp: Long, endTimestamp: Long): List<BlockUsage>
}
