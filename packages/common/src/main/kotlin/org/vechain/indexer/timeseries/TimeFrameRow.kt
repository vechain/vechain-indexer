package org.vechain.indexer.timeseries

import java.math.BigInteger
import org.vechain.indexer.accounts.TimeFrame

/** A series row on Postgres: it carries its [period], and the frame fields read through it. */
interface TimeFrameRow : TimeFrameDocument {
    override val period: TimeFramePeriod

    override val hourOfDay: Long
        get() = period.hourOfDay

    override val dayOfMonth: Long
        get() = period.dayOfMonth

    override val weekOfYear: Long
        get() = period.weekOfYear

    override val month: Long
        get() = period.month

    override val year: Long
        get() = period.year

    override val timeFrames: List<TimeFrame>
        get() = period.timeFrames

    override val blockTotal: BigInteger?
        get() = period.blockTotal

    override val hourTotal: BigInteger?
        get() = period.hourTotal

    override val dayTotal: BigInteger?
        get() = period.dayTotal

    override val weekTotal: BigInteger?
        get() = period.weekTotal

    override val monthTotal: BigInteger?
        get() = period.monthTotal

    override val yearTotal: BigInteger?
        get() = period.yearTotal
}
