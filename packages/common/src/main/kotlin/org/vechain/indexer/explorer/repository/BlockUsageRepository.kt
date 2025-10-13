package org.vechain.indexer.explorer.repository

import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.explorer.BlockUsage

@Repository
interface BlockUsageRepository : BasePagingAndSortingIndexedRepository<BlockUsage, Long> {

    /**
     * Find all block usage records within a block range.
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of BlockUsage records in the range
     */
    fun findByBlockNumberBetweenOrderByBlockNumberAsc(
        startBlock: Long,
        endBlock: Long,
    ): List<BlockUsage>

    /**
     * Find hourly block usage records within a block range. Returns only blocks where isHourly is
     * true.
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of hourly BlockUsage records in the range
     */
    fun findByBlockNumberBetweenAndIsHourlyTrueOrderByBlockNumberAsc(
        startBlock: Long,
        endBlock: Long,
    ): List<BlockUsage>

    /**
     * Find daily block usage records within a block range. Returns only blocks where isDaily is
     * true.
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of daily BlockUsage records in the range
     */
    fun findByBlockNumberBetweenAndIsDailyTrueOrderByBlockNumberAsc(
        startBlock: Long,
        endBlock: Long,
    ): List<BlockUsage>

    /**
     * Find weekly block usage records within a block range. Returns only blocks where isWeekly is
     * true.
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of weekly BlockUsage records in the range
     */
    fun findByBlockNumberBetweenAndIsWeeklyTrueOrderByBlockNumberAsc(
        startBlock: Long,
        endBlock: Long,
    ): List<BlockUsage>

    /**
     * Find monthly block usage records within a block range. Returns only blocks where isMonthly is
     * true.
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of monthly BlockUsage records in the range
     */
    fun findByBlockNumberBetweenAndIsMonthlyTrueOrderByBlockNumberAsc(
        startBlock: Long,
        endBlock: Long,
    ): List<BlockUsage>
}
