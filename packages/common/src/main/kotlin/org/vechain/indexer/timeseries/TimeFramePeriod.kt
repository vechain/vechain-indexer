package org.vechain.indexer.timeseries

import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import org.vechain.indexer.accounts.TimeFrame

/**
 * Where a series row sits in the calendar, the frames that closed at it, and its per-frame totals.
 */
data class TimeFramePeriod(
    val hourOfDay: Long,
    val dayOfMonth: Long,
    val weekOfYear: Long,
    val month: Long,
    val year: Long,
    val timeFrames: List<TimeFrame> = emptyList(),
    val blockTotal: BigInteger? = null,
    val hourTotal: BigInteger? = null,
    val dayTotal: BigInteger? = null,
    val weekTotal: BigInteger? = null,
    val monthTotal: BigInteger? = null,
    val yearTotal: BigInteger? = null,
) {
    /** Moving the series to a block: the frames [previous] closed, and the block's period. */
    data class Roll(val closed: List<TimeFrame>, val next: TimeFramePeriod)

    companion object {
        /** Frames [previous] does not share with the block close and restart at [delta]. */
        fun roll(previous: TimeFramePeriod?, blockTimestamp: Long, delta: BigInteger): Roll {
            val ts = Instant.ofEpochSecond(blockTimestamp).atZone(ZoneOffset.UTC)
            val hour = ts.hour.toLong()
            val day = ts.dayOfMonth.toLong()
            val week = ts.get(WeekFields.ISO.weekOfYear()).toLong()
            val month = ts.monthValue.toLong()
            val year = ts.year.toLong()

            val newYear = previous != null && previous.year != year
            val newMonth = newYear || previous != null && previous.month != month
            val newWeek = newYear || previous != null && previous.weekOfYear != week
            val newDay = newMonth || previous != null && previous.dayOfMonth != day
            val newHour = newDay || previous != null && previous.hourOfDay != hour

            fun total(closed: Boolean, carried: BigInteger?) =
                if (closed) delta else (carried ?: BigInteger.ZERO) + delta

            return Roll(
                closed =
                    listOfNotNull(
                        TimeFrame.HOUR.takeIf { newHour },
                        TimeFrame.DAY.takeIf { newDay },
                        TimeFrame.WEEK.takeIf { newWeek },
                        TimeFrame.MONTH.takeIf { newMonth },
                        TimeFrame.YEAR.takeIf { newYear },
                    ),
                next =
                    TimeFramePeriod(
                        hourOfDay = hour,
                        dayOfMonth = day,
                        weekOfYear = week,
                        month = month,
                        year = year,
                        blockTotal = delta,
                        hourTotal = total(newHour, previous?.hourTotal),
                        dayTotal = total(newDay, previous?.dayTotal),
                        weekTotal = total(newWeek, previous?.weekTotal),
                        monthTotal = total(newMonth, previous?.monthTotal),
                        yearTotal = total(newYear, previous?.yearTotal),
                    ),
            )
        }
    }
}
