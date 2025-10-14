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
        // - Up to 6 hours (2,160 blocks): return all blocks (~2.2k data points)
        // - Up to 1 month (259,200 blocks): return hourly aggregates (~720 data points)
        // - Up to 6 months (1,555,200 blocks): return daily aggregates (~180 data points)
        // - Up to 2 years (6,307,200 blocks): return weekly aggregates (~104 data points)
        // - Beyond 2 years: return monthly aggregates

        private const val HOURLY_THRESHOLD = 2_160L // ~6 hours
        private const val DAILY_THRESHOLD = 259_200L // ~1 month
        private const val WEEKLY_THRESHOLD = 1_555_200L // ~6 months
        private const val MONTHLY_THRESHOLD = 6_307_200L // ~2 years
    }

    /**
     * Retrieves block usage data for a given block range. The granularity of the data is
     * automatically determined based on the size of the block range to optimize for reasonable data
     * point counts.
     *
     * Granularity rules:
     * - Range <= 2,160 blocks (~6 hours): All blocks (~2.2k data points)
     * - Range <= 259,200 blocks (~1 month): Hourly aggregates (~720 data points)
     * - Range <= 1,555,200 blocks (~6 months): Daily aggregates (~180 data points)
     * - Range <= 6,307,200 blocks (~2 years): Weekly aggregates (~104 data points)
     * - Range > 6,307,200 blocks: Monthly aggregates
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
