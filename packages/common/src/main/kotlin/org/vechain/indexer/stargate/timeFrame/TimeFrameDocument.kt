package org.vechain.indexer.stargate.timeFrame

import java.math.BigInteger
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.accounts.TimeFrame

interface TimeFrameDocument : IndexedDocument {
    val hourOfDay: Long
    val dayOfMonth: Long
    val weekOfYear: Long
    val month: Long
    val year: Long
    val timeFrames: List<TimeFrame>
    val blockTotal: BigInteger?
    val hourTotal: BigInteger?
    val dayTotal: BigInteger?
    val weekTotal: BigInteger?
    val monthTotal: BigInteger?
    val yearTotal: BigInteger?
}
