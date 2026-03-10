package org.vechain.indexer.explorer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.explorer.repository.BlockUsageRepository
import org.vechain.indexer.timeseries.TimeSeriesResolution
import org.vechain.indexer.utils.TimeSeriesUtils

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
     * - Range <= 2 months: Daily aggregates (~60 data points)
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

        return when (TimeSeriesUtils.selectResolution(endTimestamp - startTimestamp)) {
            TimeSeriesResolution.RAW ->
                blockUsageRepository.findAllInTimestampRange(startTimestamp, endTimestamp)
            TimeSeriesResolution.HOURLY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    blockUsageRepository::findHourlyInTimestampRange,
                    blockUsageRepository::
                        findFirstByBlockTimestampLessThanEqualOrderByBlockTimestampDesc,
                )
            TimeSeriesResolution.DAILY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    blockUsageRepository::findDailyInTimestampRange,
                    blockUsageRepository::
                        findFirstByBlockTimestampLessThanEqualOrderByBlockTimestampDesc,
                )
            TimeSeriesResolution.WEEKLY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    blockUsageRepository::findWeeklyInTimestampRange,
                    blockUsageRepository::
                        findFirstByBlockTimestampLessThanEqualOrderByBlockTimestampDesc,
                )
            TimeSeriesResolution.MONTHLY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    blockUsageRepository::findMonthlyInTimestampRange,
                    blockUsageRepository::
                        findFirstByBlockTimestampLessThanEqualOrderByBlockTimestampDesc,
                )
        }
    }
}
