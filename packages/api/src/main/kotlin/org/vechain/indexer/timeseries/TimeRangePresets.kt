package org.vechain.indexer.timeseries

import java.time.*
import java.time.temporal.TemporalAmount

enum class TimeRangePreset(val pathValue: String, val amount: TemporalAmount?) {
    ONE_HOUR("1-hour", Duration.ofHours(1)),
    ONE_DAY("1-day", Duration.ofDays(1)),
    ONE_WEEK("1-week", Duration.ofDays(7)),
    ONE_MONTH("1-month", Period.ofMonths(1)),
    ONE_YEAR("1-year", Period.ofYears(1)),
    ALL("all", null);

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
