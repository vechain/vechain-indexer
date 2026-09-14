package org.vechain.indexer.timeseries

import java.math.BigInteger
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.accounts.TimeFrame

/** One row of a time-frame series; the frame fields read through [period]. */
interface TimeFrameDocument : IndexedDocument {
    val period: TimeFramePeriod

    val hourOfDay: Long
        get() = period.hourOfDay

    val dayOfMonth: Long
        get() = period.dayOfMonth

    val weekOfYear: Long
        get() = period.weekOfYear

    val month: Long
        get() = period.month

    val year: Long
        get() = period.year

    val timeFrames: List<TimeFrame>
        get() = period.timeFrames

    val blockTotal: BigInteger?
        get() = period.blockTotal

    val hourTotal: BigInteger?
        get() = period.hourTotal

    val dayTotal: BigInteger?
        get() = period.dayTotal

    val weekTotal: BigInteger?
        get() = period.weekTotal

    val monthTotal: BigInteger?
        get() = period.monthTotal

    val yearTotal: BigInteger?
        get() = period.yearTotal
}
