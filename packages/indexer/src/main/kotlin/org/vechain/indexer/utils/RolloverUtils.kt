package org.vechain.indexer.utils

import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import org.vechain.indexer.accounts.TimeFrame

object RolloverUtils {
    data class Context(
        val prevDayTotal: BigInteger,
        val prevWeekTotal: BigInteger,
        val prevMonthTotal: BigInteger,
        val prevYearTotal: BigInteger,
        val prevDay: Long?,
        val prevWeek: Long?,
        val prevMonth: Long?,
        val prevYear: Long?,
    )

    data class RolloverResult(
        val dayTotal: BigInteger,
        val weekTotal: BigInteger,
        val monthTotal: BigInteger,
        val yearTotal: BigInteger,
        val day: Long,
        val week: Long,
        val month: Long,
        val year: Long,
        val timeFrames: List<TimeFrame>,
    )

    fun calculateRollover(blockTimestamp: Long, delta: BigInteger, ctx: Context): RolloverResult {
        val ts = Instant.ofEpochSecond(blockTimestamp).atZone(ZoneOffset.UTC)
        val blockDay = ts.dayOfMonth.toLong()
        val blockWeek = ts.get(WeekFields.ISO.weekOfYear()).toLong()
        val blockMonth = ts.monthValue.toLong()
        val blockYear = ts.year.toLong()

        val prevDay = ctx.prevDay ?: blockDay
        val prevWeek = ctx.prevWeek ?: blockWeek
        val prevMonth = ctx.prevMonth ?: blockMonth
        val prevYear = ctx.prevYear ?: blockYear

        val timeFrames = mutableListOf<TimeFrame>()

        val dayTotal =
            if (prevDay != blockDay || prevMonth != blockMonth || prevYear != blockYear) {
                timeFrames += TimeFrame.DAY
                delta
            } else {
                ctx.prevDayTotal + delta
            }

        val weekTotal =
            if (prevWeek != blockWeek || prevYear != blockYear) {
                timeFrames += TimeFrame.WEEK
                delta
            } else {
                ctx.prevWeekTotal + delta
            }

        val monthTotal =
            if (prevMonth != blockMonth || prevYear != blockYear) {
                timeFrames += TimeFrame.MONTH
                delta
            } else {
                ctx.prevMonthTotal + delta
            }

        val yearTotal =
            if (prevYear != blockYear) {
                timeFrames += TimeFrame.YEAR
                delta
            } else {
                ctx.prevYearTotal + delta
            }

        return RolloverResult(
            dayTotal = dayTotal,
            weekTotal = weekTotal,
            monthTotal = monthTotal,
            yearTotal = yearTotal,
            day = blockDay,
            week = blockWeek,
            month = blockMonth,
            year = blockYear,
            timeFrames = timeFrames,
        )
    }
}
