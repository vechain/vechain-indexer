package org.vechain.indexer.timeseries

import java.time.*
import java.time.temporal.TemporalAmount
import org.vechain.indexer.rest.CachePolicy

/** A window's width, and — since a wider one dilutes any new point — how long it may be cached. */
enum class TimeRangePreset(
    val pathValue: String,
    val amount: TemporalAmount?,
    val cachePolicy: CachePolicy,
) {
    ONE_HOUR("1-hour", Duration.ofHours(1), CachePolicy.TEN_MINUTES),
    ONE_DAY("1-day", Duration.ofDays(1), CachePolicy.TEN_MINUTES),
    ONE_WEEK("1-week", Duration.ofDays(7), CachePolicy.HOURLY),
    ONE_MONTH("1-month", Period.ofMonths(1), CachePolicy.DAILY),
    ONE_YEAR("1-year", Period.ofYears(1), CachePolicy.DAILY),
    ALL("all", null, CachePolicy.DAILY);

    companion object {
        fun fromPathValue(value: String): TimeRangePreset =
            entries.firstOrNull { it.pathValue == value }
                ?: throw IllegalArgumentException("Invalid range: $value")
    }

    fun computeAfterTimestamp(now: Instant): Long =
        when (amount) {
            is Duration -> now.minus(amount).epochSecond
            is Period -> ZonedDateTime.ofInstant(now, ZoneOffset.UTC).minus(amount).toEpochSecond()
            null -> 0L
            else -> throw IllegalStateException("Unsupported TemporalAmount: $amount")
        }
}
