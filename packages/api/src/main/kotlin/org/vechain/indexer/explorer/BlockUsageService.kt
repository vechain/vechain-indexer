package org.vechain.indexer.explorer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.explorer.repository.BlockUsageRepository

@Profile("explorer", "block-usage")
@Service
open class BlockUsageService(private val blockUsageRepository: BlockUsageRepository) {
    companion object {
        // Time-based thresholds for determining data granularity
        // Thresholds are in seconds based on typical data points for visualization:
        // - Up to 1 hour (3,600 seconds): return all blocks (~360 data points at 10s/block)
        // - Up to 1 week (604,800 seconds): return hourly aggregates (~168 data points)
        // - Up to 1 month (2,592,000 seconds): return daily aggregates (~30 data points)
        // - Up to 1 year (31,536,000 seconds): return weekly aggregates (~52 data points)
        // - Beyond 1 year: return monthly aggregates

        private const val HOURLY_THRESHOLD = 3_600L // 1 hour in seconds
        private const val DAILY_THRESHOLD = 604_800L // 1 week in seconds
        private const val WEEKLY_THRESHOLD = 2_592_000L // 1 month in seconds (30 days)
        private const val MONTHLY_THRESHOLD = 31_536_000L // 1 year in seconds (365 days)
    }

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
