package org.vechain.indexer.timeseries

import java.sql.PreparedStatement
import java.sql.ResultSet
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.postgres.PostgresHex.bytes

/** The columns every series table shares after `block_number`, in the order [bind] sets them. */
object TimeFrameColumns {
    val COLUMNS =
        listOf(
            "block_id",
            "block_timestamp",
            "hour_of_day",
            "day_of_month",
            "week_of_year",
            "month",
            "year",
            "time_frames",
            "block_total",
            "hour_total",
            "day_total",
            "week_total",
            "month_total",
            "year_total",
        )

    /** Binds `block_number` and [COLUMNS] from parameter 1 and returns the next free index. */
    fun bind(ps: PreparedStatement, d: TimeFrameDocument, frameType: String): Int {
        val p = d.period
        ps.setLong(1, d.blockNumber)
        ps.setBytes(2, bytes(d.blockId))
        ps.setLong(3, d.blockTimestamp)
        ps.setLong(4, p.hourOfDay)
        ps.setLong(5, p.dayOfMonth)
        ps.setLong(6, p.weekOfYear)
        ps.setLong(7, p.month)
        ps.setLong(8, p.year)
        ps.setArray(
            9,
            ps.connection.createArrayOf(frameType, p.timeFrames.map { it.name }.toTypedArray()),
        )
        ps.setBigDecimal(10, p.blockTotal?.toBigDecimal())
        ps.setBigDecimal(11, p.hourTotal?.toBigDecimal())
        ps.setBigDecimal(12, p.dayTotal?.toBigDecimal())
        ps.setBigDecimal(13, p.weekTotal?.toBigDecimal())
        ps.setBigDecimal(14, p.monthTotal?.toBigDecimal())
        ps.setBigDecimal(15, p.yearTotal?.toBigDecimal())
        return 16
    }

    fun period(rs: ResultSet): TimeFramePeriod =
        TimeFramePeriod(
            hourOfDay = rs.getLong("hour_of_day"),
            dayOfMonth = rs.getLong("day_of_month"),
            weekOfYear = rs.getLong("week_of_year"),
            month = rs.getLong("month"),
            year = rs.getLong("year"),
            timeFrames =
                (rs.getArray("time_frames").array as Array<*>).map {
                    TimeFrame.valueOf(it as String)
                },
            blockTotal = rs.getBigDecimal("block_total")?.toBigIntegerExact(),
            hourTotal = rs.getBigDecimal("hour_total")?.toBigIntegerExact(),
            dayTotal = rs.getBigDecimal("day_total")?.toBigIntegerExact(),
            weekTotal = rs.getBigDecimal("week_total")?.toBigIntegerExact(),
            monthTotal = rs.getBigDecimal("month_total")?.toBigIntegerExact(),
            yearTotal = rs.getBigDecimal("year_total")?.toBigIntegerExact(),
        )
}
