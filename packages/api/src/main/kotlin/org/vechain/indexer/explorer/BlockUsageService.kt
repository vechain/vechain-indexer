package org.vechain.indexer.explorer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.timeseries.TimeSeriesResolution
import org.vechain.indexer.utils.TimeSeriesUtils
import org.vechain.indexer.utils.TimeValidationUtils

@Profile("explorer")
@Service
open class BlockUsageService(private val repository: BlockUsageReadRepository) {
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
        TimeValidationUtils.validateTimestamps(
            startTimestamp,
            endTimestamp,
            "startTimestamp",
            "endTimestamp",
        )

        return when (TimeSeriesUtils.selectResolution(endTimestamp - startTimestamp)) {
            TimeSeriesResolution.RAW ->
                repository.findAllInTimestampRange(startTimestamp, endTimestamp)
            TimeSeriesResolution.HOURLY -> sampled(TimeFrame.HOUR, startTimestamp, endTimestamp)
            TimeSeriesResolution.DAILY -> sampled(TimeFrame.DAY, startTimestamp, endTimestamp)
            TimeSeriesResolution.WEEKLY -> sampled(TimeFrame.WEEK, startTimestamp, endTimestamp)
            TimeSeriesResolution.MONTHLY -> sampled(TimeFrame.MONTH, startTimestamp, endTimestamp)
        }
    }

    private fun sampled(frame: TimeFrame, from: Long, to: Long): List<BlockUsage> =
        TimeSeriesUtils.getBookendedRecords(
            from,
            to,
            { after, before -> repository.findFrameInTimestampRange(frame, after, before) },
            repository::findLatestAtOrBefore,
        )
}
