package org.vechain.indexer.explorer.repository

import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.explorer.BlockUsage

@Repository
interface BlockUsageRepository : BasePagingAndSortingIndexedRepository<BlockUsage, Long> {

    /**
     * Find all block usage records within a block range (inclusive).
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of BlockUsage records in the range, ordered by block number ascending
     */
    @Query(value = "{ '_id': { \$gte: ?0, \$lte: ?1 } }", sort = "{ '_id': 1 }")
    fun findAllInBlockRange(startBlock: Long, endBlock: Long): List<BlockUsage>

    /**
     * Find hourly block usage records within a block range (inclusive). Returns blocks where
     * isHourly is true, plus the exact start and end blocks as boundaries.
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of hourly BlockUsage records in the range plus boundaries, ordered by block
     *   number ascending
     */
    @Query(
        value =
            "{ '_id': { \$gte: ?0, \$lte: ?1 }, \$or: [{ 'isHourly': true }, { '_id': ?0 }, { '_id': ?1 }] }",
        sort = "{ '_id': 1 }",
    )
    fun findHourlyInBlockRange(startBlock: Long, endBlock: Long): List<BlockUsage>

    /**
     * Find daily block usage records within a block range (inclusive). Returns blocks where isDaily
     * is true, plus the exact start and end blocks as boundaries.
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of daily BlockUsage records in the range plus boundaries, ordered by block
     *   number ascending
     */
    @Query(
        value =
            "{ '_id': { \$gte: ?0, \$lte: ?1 }, \$or: [{ 'isDaily': true }, { '_id': ?0 }, { '_id': ?1 }] }",
        sort = "{ '_id': 1 }",
    )
    fun findDailyInBlockRange(startBlock: Long, endBlock: Long): List<BlockUsage>

    /**
     * Find weekly block usage records within a block range (inclusive). Returns blocks where
     * isWeekly is true, plus the exact start and end blocks as boundaries.
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of weekly BlockUsage records in the range plus boundaries, ordered by block
     *   number ascending
     */
    @Query(
        value =
            "{ '_id': { \$gte: ?0, \$lte: ?1 }, \$or: [{ 'isWeekly': true }, { '_id': ?0 }, { '_id': ?1 }] }",
        sort = "{ '_id': 1 }",
    )
    fun findWeeklyInBlockRange(startBlock: Long, endBlock: Long): List<BlockUsage>

    /**
     * Find monthly block usage records within a block range (inclusive). Returns blocks where
     * isMonthly is true, plus the exact start and end blocks as boundaries.
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of monthly BlockUsage records in the range plus boundaries, ordered by block
     *   number ascending
     */
    @Query(
        value =
            "{ '_id': { \$gte: ?0, \$lte: ?1 }, \$or: [{ 'isMonthly': true }, { '_id': ?0 }, { '_id': ?1 }] }",
        sort = "{ '_id': 1 }",
    )
    fun findMonthlyInBlockRange(startBlock: Long, endBlock: Long): List<BlockUsage>
}
