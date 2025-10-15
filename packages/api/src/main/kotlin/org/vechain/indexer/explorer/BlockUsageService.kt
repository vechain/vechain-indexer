package org.vechain.indexer.explorer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.explorer.repository.BlockUsageRepository

@Profile("explorer", "block-usage")
@Service
open class BlockUsageService(private val blockUsageRepository: BlockUsageRepository) {
    companion object {
        // VeChain produces ~1 block every 10 seconds = 6 blocks/minute = 360 blocks/hour
        // Thresholds based on typical data points for visualization:
        // - Up to 1 hour (360 blocks): return all blocks (~360 data points)
        // - Up to 1 week (60,480 blocks): return hourly aggregates (~168 data points)
        // - Up to 1 month (259,200 blocks): return daily aggregates (~30 data points)
        // - Up to 1 year (3,153,600 blocks): return weekly aggregates (~52 data points)
        // - Beyond 1 year: return monthly aggregates

        private const val HOURLY_THRESHOLD = 360L // ~1 hour
        private const val DAILY_THRESHOLD = 60_480L // ~1 week
        private const val WEEKLY_THRESHOLD = 259_200L // ~1 month
        private const val MONTHLY_THRESHOLD = 3_153_600L // ~1 year
    }

    /**
     * Retrieves block usage data for a given block range. The granularity of the data is
     * automatically determined based on the size of the block range to optimize for reasonable data
     * point counts.
     *
     * Granularity rules:
     * - Range <= 360 blocks (~1 hour): All blocks (~360 data points)
     * - Range <= 60,480 blocks (~1 week): Hourly aggregates (~168 data points)
     * - Range <= 259,200 blocks (~1 month): Daily aggregates (~30 data points)
     * - Range <= 3,153,600 blocks (~1 year): Weekly aggregates (~52 data points)
     * - Range > 3,153,600 blocks: Monthly aggregates
     *
     * @param startBlock The starting block number (inclusive)
     * @param endBlock The ending block number (inclusive)
     * @return List of BlockUsage records matching the criteria
     */
    open fun getBlockUsage(startBlock: Long, endBlock: Long): List<BlockUsage> {
        require(startBlock >= 0) { "startBlock must be non-negative" }
        require(endBlock >= startBlock) { "endBlock must be greater than or equal to startBlock" }

        val blockRange = endBlock - startBlock

        return when {
            blockRange <= HOURLY_THRESHOLD -> {
                // Return all blocks for small ranges
                blockUsageRepository.findAllInBlockRange(startBlock, endBlock)
            }
            blockRange <= DAILY_THRESHOLD -> {
                // Return hourly aggregates
                blockUsageRepository.findHourlyInBlockRange(startBlock, endBlock)
            }
            blockRange <= WEEKLY_THRESHOLD -> {
                // Return daily aggregates
                blockUsageRepository.findDailyInBlockRange(startBlock, endBlock)
            }
            blockRange <= MONTHLY_THRESHOLD -> {
                // Return weekly aggregates
                blockUsageRepository.findWeeklyInBlockRange(startBlock, endBlock)
            }
            else -> {
                // Return monthly aggregates
                blockUsageRepository.findMonthlyInBlockRange(startBlock, endBlock)
            }
        }
    }
}
