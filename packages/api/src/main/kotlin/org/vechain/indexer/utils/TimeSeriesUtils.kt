package org.vechain.indexer.utils

import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.timeseries.TimeSeriesRecord

object TimeSeriesUtils {
    // Time-based thresholds for determining data granularity
    // Thresholds are in seconds based on typical data points for visualization:
    // - Up to 1 hour (4,000 seconds): return all blocks (~360 data points at 10s/block)
    // - Up to 1 week (700,800 seconds): return hourly aggregates (~168 data points)
    // - Up to 2 months (6,000,000 seconds): return daily aggregates (~60 data points)
    // - Up to 1 year (35,000,000 seconds): return weekly aggregates (~52 data points)
    // - Beyond 1 year: return monthly aggregates

    const val HOURLY_THRESHOLD = 4_000L
    const val DAILY_THRESHOLD = 700_000L
    const val WEEKLY_THRESHOLD = 6_000_000L
    const val MONTHLY_THRESHOLD = 35_000_000L

    /**
     * Retrieve historic time series data for a given range of timestamps. This method fetches data
     * from the repository based on the provided `after` and `before` timestamps, extracts the
     * relevant values, and returns a sparse time series list. Results will include bookends
     * representing the start and end of the range
     *
     * @param after The starting timestamp (inclusive).
     * @param before The ending timestamp (inclusive).
     * @param findByBlockTimestampBetween A function to find records between two timestamps.
     * @param findLatestBeforeOrAtBlockTimestamp A function to find the latest record before or at a
     *   given timestamp.
     * @param valueExtractor A function to extract the value from the record.
     * @return A list of TimeSeriesRecord containing the timestamp and extracted value.
     */
    fun <T : IndexedDocument, R : Any> getHistoricTimeSeries(
        after: Long,
        before: Long,
        findByBlockTimestampBetween: (Long, Long) -> List<T>,
        findLatestBeforeOrAtBlockTimestamp: (Long) -> T?,
        valueExtractor: (T) -> R,
    ): List<TimeSeriesRecord<R>> {
        val data = findByBlockTimestampBetween(after - 1, before + 1)
        if (data.isEmpty()) return emptyList()

        val firstRecord = data.first()
        val startBookend =
            if (firstRecord.blockTimestamp > after) {
                findLatestBeforeOrAtBlockTimestamp(after)?.let {
                    TimeSeriesRecord(after, valueExtractor(it))
                }
            } else {
                null
            }

        val lastRecord = data.last()
        val endBookend =
            if (lastRecord.blockTimestamp < before) {
                TimeSeriesRecord(before, valueExtractor(lastRecord))
            } else {
                null
            }

        val records = mutableListOf<TimeSeriesRecord<R>>()
        startBookend?.let { records.add(it) }
        records.addAll(
            data.map {
                TimeSeriesRecord((it as IndexedDocument).blockTimestamp, valueExtractor(it))
            }
        )
        endBookend?.let { records.add(it) }

        return sparsify(records)
    }

    /**
     * Sparsify the data. We should not have two consecutive records with the same value unless one
     * of them is the last record.
     *
     * This is useful to reduce the size of the time series data, especially when the data is
     * sparse.
     *
     * @param timeSeriesList The full list of time series records. (must be sorted by timestamp
     *   ascending)
     * @return A sparsified list of time series records.
     */
    fun <T : Any> sparsify(timeSeriesList: List<TimeSeriesRecord<T>>): List<TimeSeriesRecord<T>> {
        if (timeSeriesList.isEmpty()) return emptyList()

        val result = mutableListOf<TimeSeriesRecord<T>>()

        var prevValue: TimeSeriesRecord<T>? = null

        // Iterate through the time series records except the last one
        for (i in 0 until timeSeriesList.size - 1) {
            val record = timeSeriesList[i]

            // Enforce ordering by timestamp
            prevValue?.let { enforceOrdering(it, record) }

            if (record.value != prevValue?.value) {
                result.add(record)
            }

            prevValue = record
        }

        val lastRecord = timeSeriesList.last()

        // Check the last record separately
        if (result.isNotEmpty()) {
            enforceOrdering(result.last(), lastRecord)
        }

        // Always add the last record
        result.add(timeSeriesList.last())

        return result
    }

    private fun enforceOrdering(prevValue: TimeSeriesRecord<*>, currentValue: TimeSeriesRecord<*>) {
        if (prevValue.timestamp >= currentValue.timestamp) {
            throw IllegalStateException(
                "Time series records must be sorted by timestamp ascending."
            )
        }
    }
}
