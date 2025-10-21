package org.vechain.indexer.explorer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.explorer.repository.BlockUsageRepository
import org.vechain.indexer.utils.TimeSeriesUtils.DAILY_THRESHOLD
import org.vechain.indexer.utils.TimeSeriesUtils.HOURLY_THRESHOLD
import org.vechain.indexer.utils.TimeSeriesUtils.MONTHLY_THRESHOLD
import org.vechain.indexer.utils.TimeSeriesUtils.WEEKLY_THRESHOLD

@Profile("explorer", "block-usage")
@Service
open class BlockUsageService(private val blockUsageRepository: BlockUsageRepository) {
    /**
     * Retrieves block usage data for a given timestamp range. The granularity of the data is
     * automatically determined based on the size of the time range to optimize for reasonable data
     * point counts.
     *
     * Granularity rules:
     * - Range <= 1 hour: All blocks (~360 data points)
     * - Range <= 1 week: Hourly aggregates (~168 data points)
     * - Range <= 1 month: Daily aggregates (~30 data points)
     * - Range <= 1 year: Weekly aggregates (~52 data points)
     * - Range > 1 year: Monthly aggregates
     *
     * @param startTimestamp The starting timestamp in seconds (inclusive)
     * @param endTimestamp The ending timestamp in seconds (inclusive)
     * @return List of BlockUsage records matching the criteria
     */
    open fun getBlockUsage(startTimestamp: Long, endTimestamp: Long): List<BlockUsage> {
        require(startTimestamp >= 0) { "startTimestamp must be non-negative" }
        require(endTimestamp >= startTimestamp) {
            "endTimestamp must be greater than or equal to startTimestamp"
        }

        val timeRange = endTimestamp - startTimestamp

        return when {
            timeRange <= HOURLY_THRESHOLD -> {
                // Return all blocks for small ranges
                blockUsageRepository.findAllInTimestampRange(startTimestamp, endTimestamp)
            }
            timeRange <= DAILY_THRESHOLD -> {
                // Return hourly aggregates
                blockUsageRepository.findHourlyInTimestampRange(startTimestamp, endTimestamp)
            }
            timeRange <= WEEKLY_THRESHOLD -> {
                // Return daily aggregates
                blockUsageRepository.findDailyInTimestampRange(startTimestamp, endTimestamp)
            }
            timeRange <= MONTHLY_THRESHOLD -> {
                // Return weekly aggregates
                blockUsageRepository.findWeeklyInTimestampRange(startTimestamp, endTimestamp)
            }
            else -> {
                // Return monthly aggregates
                blockUsageRepository.findMonthlyInTimestampRange(startTimestamp, endTimestamp)
            }
        }
    }
}
